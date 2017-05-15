(ns eponai.web.chat
  (:require
    [om.next :as om]
    [eponai.common.database :as db]
    [eponai.common.shared :as shared]
    [eponai.client.chat :as chat]
    [eponai.web.sente :as sente]
    [taoensso.timbre :refer [debug]]))

(defn chat-update-handler [reconciler]
  (letfn [(has-update [store-id basis-t]
            (let [curr-store-id (chat/current-store-id reconciler)]
              (if-not (= store-id curr-store-id)
                (debug "Message store-id and current route store id differ. Ignoring update. "
                       {:current-store-id curr-store-id
                        :message-store-id store-id})
                (let [db (db/to-db reconciler)
                      current-basis-t (chat/chat-basis-t db store-id)]
                  (debug "Got update for store-id: " store-id " basis-t: " basis-t "current-basis-t: " current-basis-t)
                  (if (and (some? current-basis-t) (>= current-basis-t basis-t))
                    (debug "Already had the chat update.")
                    (do (debug "Will request query chat because we're not up to date.")
                        (om/transact! reconciler (into [(list 'chat/queue-update {:store-id store-id :basis-t basis-t})]
                                                       (om/transform-reads reconciler [:query/chat])))))))))]
    (reify
      sente/ISenteEventHandler
      (event-received [this event-id event-data]
        (if (= :store-chat/update event-id)
          (has-update (:store-id event-data) (:basis-t event-data))
          (debug "Not handling our event, event-id: " event-id " whole event-data: " event-data))))))

(defn- start-listening-event [store-id]
  [:store-chat/start-listening! {:store-id store-id}])

(defrecord StoreChatListener [sente-sender]
  chat/IStoreChatListener
  (start-listening! [this store-id]
    (sente/subscribe-event sente-sender (start-listening-event store-id)))
  (stop-listening! [this store-id]
    (sente/unsubscribe-event sente-sender (start-listening-event store-id))
    (sente/send-event sente-sender [:store-chat/stop-listening! {:store-id store-id}]))
  chat/IStopChatListener
  (shutdown! [this]
    (debug "Shutting down ChatStoreListener: " this)
    (sente/stop-sente! sente-sender)))

(defmethod shared/shared-component [:shared/store-chat-listener ::shared/prod] [reconciler _ _]
  (let [chat-event-handler (chat-update-handler reconciler)
        sente-sender (sente/delayed-start "/ws/chat" [chat-event-handler])]
    (->StoreChatListener sente-sender)))
