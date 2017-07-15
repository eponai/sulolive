(ns eponai.server.websocket
  (:require
    [clojure.core.async :as async :refer [go <! go-loop]]
    [eponai.server.external.chat :as chat]
    [com.stuartsierra.component :as component]
    [taoensso.sente :as sente]
    [taoensso.sente.server-adapters.aleph :as sente.aleph]
    [taoensso.sente.packers.transit :as sente-transit]
    [taoensso.timbre :refer [debug error warn]]
    [datascript.core :as d]
    [eponai.common.database :as db]
    [eponai.server.middleware :as middleware]
    [taoensso.timbre :as timbre]
    [suspendable.core :as suspendable]))

(defprotocol IWebsocket
  (handle-get-request [this request])
  (handler-post-request [this request]))

(defprotocol ISubscriptionStore
  (subscribe-to-store [this store-id uid])
  (unsubscribe-from-store [this store-id uid])
  (get-subscribers [this store-id])
  (remove-subscriber [this uid]))

(defn- update-subscriber [this store-id uid db-fn]
  (let [temp-store (d/tempid :db/user)
        temp-sub (d/tempid :db/user)]
    (update this :db
            (fn [db]
              (let [tx (condp = db-fn
                         :db/add [{:store/id store-id
                                   :db/id    temp-store}
                                  {:subscriber/uid uid
                                   :db/id          temp-sub}
                                  [:db/add temp-sub :subscriber/subscriptions temp-store]]
                         :db/retract (when-let [[sub store] (-> db
                                                                (db/find-with
                                                                  {:find    '[?sub ?store]
                                                                   :where   '[[?store :store/id ?store-id]
                                                                              [?sub :subscriber/uid ?uid]
                                                                              [?sub :subscriber/subscriptions ?store]]
                                                                   :symbols {'?uid      uid
                                                                             '?store-id store-id}})
                                                                (first))]
                                       [[:db/retract sub :subscriber/subscriptions store]]))]
                (cond-> db (some? tx) (d/db-with tx)))))))


(defrecord DatascriptSubscriptionStore [db]
  ISubscriptionStore
  (subscribe-to-store [this store-id uid]
    (debug "Adding uid: " uid " to store listening on: " store-id)
    (update-subscriber this store-id uid :db/add))
  (unsubscribe-from-store [this store-id uid]
    (debug "Removing uid: " uid " to store listening on: " store-id)
    (update-subscriber this store-id uid :db/retract))
  (get-subscribers [this store-id]
    (db/all-with db {:where   '[[?store :store/id ?store-id]
                                [?sub :subscriber/uid ?e]
                                [?sub :subscriber/subscriptions ?store]]
                     :symbols {'?store-id store-id}}))
  (remove-subscriber [this uid]
    (debug "Removing :uid: " uid)
    (let [sub (db/one-with db {:where   '[[?e :subscriber/uid ?uid]]
                               :symbols {'?uid uid}})]
      (cond-> this
              (some? sub)
              (update :db d/db-with [[:db.fn/retractEntity sub]])))))

(defn datascript-subscription-store []
  (-> (d/create-conn {:store/id                 {:db/index true
                                                 :db/unique :db.unique/identity}
                      :subscriber/uid           {:db/unique :db.unique/identity
                                                 :db/index true}
                      :subscriber/subscriptions {:db/cardinality :db.cardinality/many
                                                 :db/valueType   :db.type/ref}})
      (d/db)
      (->DatascriptSubscriptionStore)))

(defn- <send-store-ids-to-clients [sente control-chan store-id-stream subscription-store]
  (let [{:keys [ch-recv connected-uids send-fn]} sente
        handler-wrapper (fn [event-handler handler-name v]
                          (try
                            (event-handler v)
                            (catch Exception e
                              (.printStackTrace e)
                              (error "Exception in " handler-name ": " e)
                              (debug "Will continue " handler-name " until there's an Error."))
                            (catch Error e
                              (.printStackTrace e)
                              (error "Error in " handler-name ": " e)
                              (async/put! control-chan e))))
        sente-event-handler
        (fn [{:keys [event uid client-id] :as v}]
          (let [[event-id event-data] event]
            (cond
              (= event-id :store-chat/start-listening!)
              (if (some? (:store-id event-data))
                (swap! subscription-store subscribe-to-store (:store-id event-data) uid)
                (debug "No :store-id key found in event: " event))

              (= event-id :store-chat/stop-listening!)
              (if (some? (:store-id event-data))
                (swap! subscription-store unsubscribe-from-store (:store-id event-data) uid)
                (debug "No :store-id key found in event: " event))

              (= event-id :chsk/uidport-close)
              (let [uid event-data]
                (swap! subscription-store remove-subscriber uid))

              ;; Ignored events
              (#{:chsk/ws-ping :chsk/uidport-open :chsk/bad-event :chsk/bad-package} event-id)
              nil

              :else
              (debug "Unhandled event: " [:uid uid :event event]))))
        store-id-stream-handler
        (fn [v]
          (let [store-id (:store-id v)]
            (debug "Got store-id: " store-id)
            (if (nil? store-id)
              (warn "No store-id found in value returned from store-id stream. Value was: " v)
              (let [uids-listening-for-id (get-subscribers @subscription-store store-id)]
                ;; TODO: Remove this because it's huge.
                (debug "Uids listening for store-id: " store-id " -> " (vec uids-listening-for-id))
                (doseq [uid uids-listening-for-id]
                  (if (contains? (:any @connected-uids) uid)
                    (do
                      (debug "Sending store id update to uid: " uid [:store-id store-id])
                      (send-fn uid [:store-chat/update (select-keys v [:store-id :basis-t])])
                      (debug "Sent store id update to uid: " uid))
                    (do
                      (debug "uid was no longer connected to the server: " uid " for " store-id)
                      (swap! subscription-store unsubscribe-from-store store-id uid))))))))]

    (async/thread
      (loop []
        (let [[v c] (async/alts!! [ch-recv store-id-stream control-chan])]
          (condp = c
            control-chan
            (debug "Exiting chat websocket due to value on control-channel: " v)

            ch-recv
            (do
              (handler-wrapper sente-event-handler "chat-websocket" v)
              (recur))

            store-id-stream
            (if-not v
              (debug "store-id-stream closed. Bailing out.")
              (do
                (handler-wrapper store-id-stream-handler "store-stream-handler" v)
                (recur)))))))))

(defrecord StoreChatWebsocket [chat]
  component/Lifecycle
  (start [this]
    (if (::started? this)
      this
      (let [{:keys [ch-recv] :as sente} (sente/make-channel-socket!
                                          (sente.aleph/get-sch-adapter)
                                          {:packer        (sente-transit/get-transit-packer)
                                           :csrf-token-fn (fn [request]
                                                            (::middleware/anti-forgery-token request))
                                           :user-id-fn    (fn [{:keys [client-id] :as ring-req}]
                                                            ;; client-id is assoc'ed by sente
                                                            ;; before calling this fn.
                                                            client-id
                                                            ;; TODO: Figure out if we want the uid to be the same for
                                                            ;; all users for some reason with this code:
                                                            ;; (get-in ring-req [:identity :email])
                                                            )})
            control-chan (async/chan 1)
            subscription-store (atom (datascript-subscription-store))
            sends-chan (<send-store-ids-to-clients sente control-chan (chat/chat-update-stream chat) subscription-store)]
        (assoc this ::started? true
                    :sente sente
                    :subscription-store subscription-store
                    :ch-recv ch-recv
                    :sends-chan sends-chan
                    :stop-fn #(async/close! control-chan)))))
  (stop [this]
    (when (::started? this)
      ((:stop-fn this))
      (async/close! (:ch-recv this))
      (let [sends (:sends-chan this)
            [_ c] (async/alts!! [sends (async/timeout 3000)])]
        (if (= c sends)
          (debug "Stopped StoreChatWebsocket successfully")
          (debug "Timed out stopping StoreChatWebsocket..."))))
    (dissoc this ::started? :sente :ch-recv :stop-fn :sends-chan :subscription-store))

  suspendable/Suspendable
  (suspend [this]
    this)
  (resume [this old-this]
    (if (::started? this)
      (reduce-kv assoc this (select-keys old-this [::started? :sente :ch-recv :stop-fn :sends-chan :subscription-store]))
      (do (component/stop old-this)
          (component/start this))))

  IWebsocket
  (handle-get-request [this request]
    ((get-in this [:sente :ajax-get-or-ws-handshake-fn])
      request))
  (handler-post-request [this request]
    ((get-in this [:sente :ajax-post-fn])
      request)))
