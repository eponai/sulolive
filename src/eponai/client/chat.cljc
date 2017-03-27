(ns eponai.client.chat
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]))
  (:require
    [eponai.client.routes :as routes]
    #?(:cljs [cljs.core.async :as a]
       :clj
    [clojure.core.async :as a :refer [go]])
    [om.next :as om]
    [eponai.common]
    [eponai.common.database :as db]
    [eponai.common.parser :as parser]
    [eponai.common.parser.util :as p.util]
    [taoensso.sente :as sente]
    [taoensso.sente.packers.transit :as sente.transit]
    [taoensso.timbre :refer [debug error]]
    [taoensso.timbre :as timbre]))

(defprotocol IStoreChatListener
  (start-listening! [this store-id])
  (stop-listening! [this store-id])
  (has-update [this store-id basis-t] "Has update on a store-id with datomic db basis-t")
  (shutdown! [this]))

(defn- read-basis-t-graph [db]
  (::parser/read-basis-t-graph
    (db/entity db [:ui/singleton ::parser/read-basis-t])))

(defn chat-basis-t [db store-id]
  (try
    (p.util/get-basis-t (read-basis-t-graph db) :query/chat {:store-id store-id})
    (catch #?@(:clj [Exception e] :cljs [:default e])
           (error "Error getting current basis-t for chat: " e)
      ;; TODO: We should be able to display some kind of "something went wrong"
      ;;       display/banner to the user. For now this just disables the chat.
      (throw e))))

(defn queue-basis-t-tx
  "Returns a transaction for adding a basis-t to a store's chat queue (request queue)."
  [store-id basis-t]
  [{:ui.store-chat-queue/store-id store-id
    :ui.store-chat-queue/basis-t  basis-t}])

(defn queued-basis-t [db store-id]
  (db/one-with db {:where   '[[?queue :ui.store-chat-queue/store-id ?store-id]
                              [?queue :ui.store-chat-queue/basis-t ?e]]
                   :symbols {'?store-id store-id}}))

(defn current-store-id
  "Given a reconciler, component, app-state(conn) or db, returns the current store-id.
  It's based on routing, so we'll only return a store-id if we're at a store."
  [x]
  {:post [(or (nil? %) (number? %))]}
  (some-> (routes/current-route x)
          (get-in [:route-params :store-id])
          (eponai.common/parse-long)))

#?(:cljs
   (defrecord StoreChatListener [reconciler-atom send-fn stop-fn]
     IStoreChatListener
     (start-listening! [this store-id]
       ((force send-fn) [:store-chat/start-listening! {:store-id store-id}]))
     (stop-listening! [this store-id]
       ((force send-fn) [:store-chat/stop-listening! {:store-id store-id}]))
     (has-update [this store-id basis-t]
       (let [reconciler @reconciler-atom
             curr-store-id (current-store-id reconciler)]
         (if-not (= store-id curr-store-id)
           (debug "Message store-id and current route store id differ. Ignoring update. "
                  {:current-store-id curr-store-id
                   :message-store-id store-id})
           (let [db (db/to-db reconciler)
                 current-basis-t (chat-basis-t db store-id)]
             (debug "Got update for store-id: " store-id " basis-t: " basis-t "current-basis-t: " current-basis-t)
             (if (and (some? current-basis-t) (>= current-basis-t basis-t))
               (debug "Already had the chat update.")
               (do (debug "Will request query chat because we're not up to date.")
                   (om/transact! reconciler (into [`(chat/queue-update ~{:store-id store-id :basis-t basis-t})]
                                                  (om/transform-reads reconciler [:query/chat])))))))))
     (shutdown! [this]
       (debug "Shutting down ChatStoreListener: " this)
       (stop-fn))))

#?(:cljs
   (defn sente-chat-channel! []
     (sente/make-channel-socket! "/ws/chat" {:packer (sente.transit/get-transit-packer)
                                             :type   :auto})))

#?(:cljs
   (defn start-sente! [chat-listener-atom control-chan]
     (let [{:keys [send-fn ch-recv] :as sente-channel} (sente-chat-channel!)
           send-on-ready-chan (a/chan)
           ready? (atom false)
           event-handler (fn [{:keys [event send-fn] :as ws-event}]
                           (let [[event-id event-data] event]
                             (cond
                               (= :chsk/state event-id)
                               (let [[_ state-data] event-data]
                                 (reset! ready? (:open? state-data))
                                 (when @ready?
                                   (loop []
                                     ;; TODO: Re-write the send code so that we only send
                                     ;; when the state is open? (instead of using an atom).
                                     ;; Also, maybe we should keep sending the
                                     ;; :store-chat/start-listening! event everytime
                                     ;; we get a new handshake?
                                     ;; Rename to :store-chat/listen-to-id ?
                                     (when-let [queued (a/poll! send-on-ready-chan)]
                                       (send-fn queued)
                                       (recur)))))
                               (= :chsk/recv event-id)
                               (let [[our-id our-data] event-data]
                                 (cond
                                   (= :store-chat/update our-id)
                                   (do
                                     (debug "Got store-chat/update event: " ws-event)
                                     (has-update @chat-listener-atom (:store-id our-data) (:basis-t our-data)))
                                   :else
                                   (debug "Not handling our event, event-id: " our-id " whole event: " event)))
                               :else
                               (debug "Sente event we don't care about right now: " event))))]
       (go
         (loop []
           (try
             (let [[v c] (a/alts! [ch-recv control-chan])]
               (if (= c control-chan)
                 (do (debug "Control-chan was closed. Shutting down chat-store-listener..")
                     (sente/-chsk-disconnect! (:chsk sente-channel) :requested-disconnect))
                 (do
                   (event-handler v)
                   (recur))))
             (catch :default e
               (error "Exception in websocket loop: " e ", will keep going.")))))
       (fn [event]
         (debug "Sending websocket event: " event)
         (if @ready?
           (send-fn event)
           (go
             (a/>! send-on-ready-chan event)))))))

#?(:cljs
   (defn store-chat-listener [reconciler-atom]
     (let [control-chan (a/chan)
           stop-fn #(a/close! control-chan)
           chat-listener-atom (atom nil)
           send-fn (delay (start-sente! chat-listener-atom control-chan))
           chat-listener (->StoreChatListener reconciler-atom send-fn stop-fn)]
       (reset! chat-listener-atom chat-listener)
       chat-listener)))