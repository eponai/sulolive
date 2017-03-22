(ns eponai.server.websocket
  (:require
    [clojure.core.async :as a :refer [go <!]]
    [eponai.server.external.chat :as chat]
    [com.stuartsierra.component :as component]
    [suspendable.core :as suspendable]
    [taoensso.sente :as sente]
    [taoensso.sente.server-adapters.aleph :as sente.aleph]
    [taoensso.sente.packers.transit :as sente-transit]
    [taoensso.timbre :refer [debug error warn]]
    [taoensso.timbre :as timbre]))

(defprotocol IWebsocket
  (handle-get-request [this request])
  (handler-post-request [this request]))

(defn- <send-store-ids-to-clients [sente control-chan store-id-stream store-id->uids]
  (let [{:keys [ch-recv connected-uids send-fn]} sente
        event-handler (fn [{:keys [event id ?data ?reply-fn uid ring-req client-id]}]
                        (let [[event-id event-data] event]
                          (condp = event-id
                            :store-chat/start-listening!
                            (if (some? (:store-id event-data))
                              (do
                                (debug "Adding :uid: " uid " to store listening on: " (:store-id event-data))
                                (swap! store-id->uids update (:store-id event-data) (fnil conj #{}) uid))
                              (debug "No :store-id key found in event: " event))
                            :store-chat/stop-listening!
                            (if (some? (:store-id event-data))
                              (do
                                (debug "Removing :uid: " uid " to store listening on: " (:store-id event-data))
                                (swap! store-id->uids update (:store-id event-data (fnil disj #{}) uid)))
                              (debug "No :store-id key found in event: " event))
                            (debug "Unhandled event: " [:uid uid :event event]))))]
    (go
      (loop []
        (debug "Awaiting new value for websocket")
        (let [[v c] (a/alts! [ch-recv store-id-stream control-chan])]
          (condp = c
            control-chan (debug "Exiting chat websocket due to value on control-channel: " v)
            ch-recv (do
                      (try
                        (debug "Websocket event: " (dissoc v :ring-req))
                        (event-handler v)
                        (catch Exception e
                          (error "Exception in chat-websocket: " e)
                          (debug "Will continue chat-websocket until there's an Error."))
                        (catch Error e
                          (error "Error in chat-websocket: " e)
                          (a/put! control-chan e)))
                      (recur))
            store-id-stream (if-not v
                              (debug "store-id-stream closed. Bailing out.")
                              (let [store-id (:store-id v)]
                                (debug "Got store-id: " store-id)
                                (try
                                  (if (nil? store-id)
                                    (warn "No store-id found in value returned from store-id stream. Value was: " v)
                                    (let [uids-listening-for-id (get @store-id->uids store-id)]
                                      (debug "Uids listening for store-id: " store-id " -> " (vec uids-listening-for-id))
                                      (doseq [uid uids-listening-for-id]
                                       (if (contains? (:any @connected-uids) uid)
                                         (do
                                           (debug "Sending store id update to uid: " uid [:store-id store-id])
                                           (send-fn uid [:store-chat/update (select-keys v [:store-id :basis-t])])
                                           (debug "Sent store id update to uid: " uid))
                                         (do
                                           (debug "uid was no longer connected to the server(?). Removing: " uid
                                                  " from " store-id)
                                           ;; TODO: REMOVE THIS debug, it's for testing only right now.
                                           (debug "connected-uids: " @connected-uids)
                                           (swap! store-id->uids update store-id disj uid))))))
                                  ;; TODO: DRY this (it's a copy from the handler above
                                  (catch Exception e
                                    (error "Exception in chat-websocket: " e)
                                    (debug "Will continue chat-websocket until there's an Error."))
                                  (catch Error e
                                    (error "Error in chat-websocket: " e)
                                    (a/put! control-chan e)))
                                (recur)))))))))

(defrecord StoreChatWebsocket [chat]
  component/Lifecycle
  (start [this]
    (if (::started? this)
      this
      (let [{:keys [ch-recv] :as sente} (sente/make-channel-socket!
                                          (sente.aleph/get-sch-adapter)
                                          {:packer     (sente-transit/get-transit-packer)
                                           :user-id-fn (fn [ring-req]
                                                         (or (get-in ring-req [:identity :email])
                                                             ;; client-id is assoc'ed by sente
                                                             ;; before calling this fn.
                                                             (:client-id ring-req)))})
            control-chan (a/chan 1)
            store-id->uids (atom {})
            sends-chan (<send-store-ids-to-clients sente control-chan (chat/chat-update-stream chat) store-id->uids)]
        (assoc this ::started? true
                    :sente sente
                    :store-id->uids store-id->uids
                    :ch-recv ch-recv
                    :sends-chan sends-chan
                    :stop-fn #(a/close! control-chan)))))
  (stop [this]
    (when (::started? this)
      ((:stop-fn this))
      (a/close! (:ch-recv this))
      (let [sends (:sends-chan this)
            [_ c] (a/alts!! [sends (a/timeout 3000)])]
        (if (= c sends)
          (debug "Stopped StoreChatWebsocket successfully")
          (debug "Timed out stopping StoreChatWebsocket..."))))
    (dissoc this ::started? :sente :ch-recv :stop-fn :sends-chan))
  suspendable/Suspendable
  (suspend [this]
    (component/stop this))
  (resume [this old-this]
    ;; Keep the store-id->uids, since they are registered with a message sent once.
    ;; TODO: This is not realistic though. A user could lose connection and we should handle
    ;; this differently: WEB-256
    (let [store-id->uids (:store-id->uids this)]
      (swap! store-id->uids (fn [uids]
                              (merge-with merge (some-> (:store-id->uids old-this) (deref)) uids)))
      (assoc this :store-id->uids store-id->uids)))

  IWebsocket
  (handle-get-request [this request]
    ((get-in this [:sente :ajax-get-or-ws-handshake-fn])
      request))
  (handler-post-request [this request]
    ((get-in this [:sente :ajax-post-fn])
      request)))
