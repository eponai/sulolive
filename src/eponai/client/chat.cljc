(ns eponai.client.chat
  (:require
    [eponai.client.routes :as routes]
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
    (some-> (read-basis-t-graph db)
            (p.util/get-basis-t :query/chat {:store-id store-id})
            ;; Basis-t for query/chat consists of two basis-t. Once
            ;; for each db (:chat-db and :sulo-db).
            (:chat-db))
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

;; ###########
;; ## Query

(def parse-chat-message-user-query
  (memoize (fn [query]
             (p.util/focus-subquery query [:chat/messages :chat.message/user]))))

(defn datomic-chat-entity-query [store]
  {:where   '[[?e :chat/store ?store-id]]
   :symbols {'?store-id (:db/id store)}})

(defn read-chat [chat-db sulo-db query store]
  (let [chat-messages (db/pull-one-with chat-db
                                        query
                                        (datomic-chat-entity-query store))
        users (into #{} (comp (map :chat.message/user)
                              (map :db/id))
                    (:chat/messages chat-messages))
        ;; TODO: Get :chat/modes from chat-db or sulo-db.
        user-pattern (parse-chat-message-user-query query)
        user-data (db/pull-many sulo-db user-pattern (seq users))
        ret {:sulo-db-tx user-data
         :chat-db-tx chat-messages}]
    (debug "read-chat chat-db: " chat-db)
    (debug "read-chat ret: " ret)
    ret))