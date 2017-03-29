(ns eponai.client.chat
  (:require
    [eponai.client.routes :as routes]
    #?(:cljs [eponai.client.sente :as sente])
    [om.next :as om]
    [eponai.common]
    [eponai.common.database :as db]
    [eponai.common.parser :as parser]
    [eponai.common.parser.util :as p.util]
    [taoensso.timbre :refer [warn debug error]]))

(defprotocol IStoreChatListener
  (start-listening! [this store-id])
  (stop-listening! [this store-id]))

(defprotocol IStopChatListener
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
   (defn chat-update-handler [reconciler-atom]
     (letfn [(has-update [store-id basis-t]
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
                                                          (om/transform-reads reconciler [:query/chat])))))))))]
       (reify
         sente/ISenteEventHandler
         (event-received [this event-id event-data]
           (if (= :store-chat/update event-id)
             (has-update (:store-id event-data) (:basis-t event-data))
             (debug "Not handling our event, event-id: " event-id " whole event-data: " event-data)))))))

(defn- start-listening-event [store-id]
  [:store-chat/start-listening! {:store-id store-id}])

#?(:cljs
   (defrecord StoreChatListener [reconciler-atom sente-sender]
     IStoreChatListener
     (start-listening! [this store-id]
       (sente/subscribe-event sente-sender (start-listening-event store-id)))
     (stop-listening! [this store-id]
       (sente/unsubscribe-event sente-sender (start-listening-event store-id))
       (sente/send-event sente-sender [:store-chat/stop-listening! {:store-id store-id}]))
     IStopChatListener
     (shutdown! [this]
       (debug "Shutting down ChatStoreListener: " this)
       (sente/stop-sente! sente-sender))))

#?(:cljs
   (defn store-chat-listener [reconciler-atom]
     (let [chat-event-handler (chat-update-handler reconciler-atom)
           sente-sender (sente/delayed-start "/ws/chat" [chat-event-handler])]
       (->StoreChatListener reconciler-atom sente-sender))))
