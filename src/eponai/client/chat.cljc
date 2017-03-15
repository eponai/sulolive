(ns eponai.client.chat
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]))
  (:require
    [eponai.client.routes :as routes]
    #?(:cljs [cljs.core.async :as a]
        :clj [clojure.core.async :as a :refer [go]])
    [om.next :as om]
    [taoensso.sente  :as sente]
    [taoensso.sente.packers.transit :as sente.transit]
    [taoensso.timbre :refer [debug error]]))

(defprotocol IStoreChatListener
  (start-listening! [this store-id])
  (stop-listening! [this store-id])
  (has-update [this store-id])
  (shutdown! [this]))

#?(:cljs
   (defrecord StoreChatListener [reconciler-atom sente-channel id->listeners stop-fn]
     IStoreChatListener
     (start-listening! [this store-id]
       (let [msg [:store-chat/start-listening! {:store-id store-id}]]
         (debug "Putting message on sente-channel: " msg " sente: " sente-channel)
         ((:send-fn sente-channel) msg)))
     (stop-listening! [this store-id]
       ((:send-fn sente-channel) [:store-chat/stop-listening! {:store-id store-id}]))
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
   (defn store-chat-listener [reconciler-atom]
     (let [{:keys [ch-recv] :as sente-channel} (sente-chat-channel!)
           control-chan (a/chan)
           stop-fn #(a/close! control-chan)
           chat-listener (->StoreChatListener reconciler-atom
                                              sente-channel
                                              (atom {})
                                              stop-fn)
           event-handler (fn [{:keys [event id ?data send-fn] :as ws-event}]
                           (let [[event-id event-data] event]
                             (cond
                               (= :store-chat/update event-id)
                               (do
                                 (debug "Got store-chat/update event: " ws-event)
                                 (has-update chat-listener (:store-id event-data)))
                               :else
                               (debug "Unrecognized event-id: " event-id " whole event: " ws-event))))]
       ;; TODO:
       ;; Maybe start the connection only on (start-listening!)
       ;; and close it if we stop listening to all store-ids?
       ;; (How do we stop it?)
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
       chat-listener)))