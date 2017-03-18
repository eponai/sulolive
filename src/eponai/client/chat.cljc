(ns eponai.client.chat
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]))
  (:require
    [eponai.client.routes :as routes]
    #?(:cljs [cljs.core.async :as a]
       :clj
    [clojure.core.async :as a :refer [go]])
    [om.next :as om]
    [taoensso.sente :as sente]
    [taoensso.sente.packers.transit :as sente.transit]
    [taoensso.timbre :refer [debug error]]
    [taoensso.timbre :as timbre]))

(defprotocol IStoreChatListener
  (start-listening! [this store-id])
  (stop-listening! [this store-id])
  (has-update [this store-id])
  (shutdown! [this]))

#?(:cljs
   (defrecord StoreChatListener [reconciler-atom send-fn stop-fn]
     IStoreChatListener
     (start-listening! [this store-id]
       ((force send-fn) [:store-chat/start-listening! {:store-id store-id}]))
     (stop-listening! [this store-id]
       ((force send-fn) [:store-chat/stop-listening! {:store-id store-id}]))
     (has-update [this store-id]
       (let [current-store-id (get-in (routes/current-route @reconciler-atom) [:route-params :store-id])]
         (if (= (str store-id) (str current-store-id))
           (do
             (debug "Got update for store-id: " store-id)
             (om/transact! @reconciler-atom (om/transform-reads @reconciler-atom [:query/chat])))
           (debug "Message store-id and current route store id differ. Ignoring update. "
                  {:current-store-id current-store-id
                   :message-store-id store-id}))))
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
                                     (has-update @chat-listener-atom (:store-id our-data)))
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
               (debug "Exception in websocket loop: " e ", will keep going.")))))
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