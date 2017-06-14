(ns eponai.server.external.chat
  (:require [datomic.api :as d]
            [eponai.common.format :as format]
            [eponai.common.database :as db]
            [eponai.server.datomic.query :as query]
            [com.stuartsierra.component :as component]
            [suspendable.core :as suspendable]
            [om.next :as om]
            [clojure.core.async :as async]
            [taoensso.timbre :refer [debug error]]
            [eponai.common.parser.util :as parser.util]
            [eponai.server.external.datomic :as datomic]
            [eponai.client.chat :as client.chat]))

(defprotocol IWriteStoreChat
  (write-message [this store user message] "write a message from a user to a store's chat."))

(defprotocol IReadStoreChat
  (initial-read [this store query]
    "Initial call to get chat entity and (maybe?) some of its messages. Returns map with keys #{:sulo-db-tx :chat-db-tx}")
  (read-messages [this store query last-read-chat-db last-read-sulo-db]
    "Read messages from last time. Returns map with keys #{:sulo-db-tx :chat-db-tx}")
  (last-read [this] "Some identifier that can be used in read-messages to only get what's changed.")
  (chat-update-stream [this] "Stream of store-id's which have new messages")
  (sync-up-to! [this t]))

;; #############################
;; ### Datomic implementation

(defn- updated-store-ids [{:keys [db-after] :as tx-report}]
  (let [ret (d/q '{:find  [[?store-id ...]]
                   :where [[$ ?chat ?chat-messages-attr _]
                           [$db ?chat ?chat-store-attr ?store-id]]
                   :in    [$ $db ?chat-messages-attr ?chat-store-attr]}
                 (:tx-data tx-report)
                 db-after
                 (:id (d/attribute db-after :chat/messages))
                 (:id (d/attribute db-after :chat/store)))]
    (debug "Found store-ids: " ret " in tx-report: " tx-report)
    ret))

(defn chat-db [chat]
  (d/db (:conn (:chat-datomic chat))))

(defrecord DatomicChat [sulo-datomic chat-datomic]
  component/Lifecycle
  (start [this]
    (if (::started? this)
      this
      (let [store-id-chan (async/chan (async/sliding-buffer 1000))
            listener (datomic/add-tx-listener
                       chat-datomic
                       (fn [tx-report]
                         (doseq [store-id (updated-store-ids tx-report)]
                           (async/put! store-id-chan {:event-type :store-id
                                                      :store-id   store-id
                                                      :basis-t    (d/basis-t (:db-after tx-report))})))
                       (fn [error]
                         (async/put! store-id-chan {:exception  error
                                                    :event-type :exception})))]
        (assoc this ::started? true
                    :listener listener
                    :store-id-chan store-id-chan))))
  (stop [this]
    (when (::started? this)
      (datomic/remove-tx-listener chat-datomic (:listener this))
      (async/close! (:store-id-chan this)))
    (dissoc this ::started? :listener :store-id-chan))

  IWriteStoreChat
  (write-message [this store user message]
    (let [tx (format/chat-message (chat-db this) store user message)]
      (db/transact (:conn chat-datomic) tx)))

  IReadStoreChat
  (initial-read [this store query]
    (client.chat/read-chat (chat-db this)
                           (db/db (:conn sulo-datomic))
                           query
                           store
                           client.chat/message-limit))
  (read-messages [this store query last-read-chat-db last-read-sulo-db]
    (let [db (chat-db this)
          db-history (d/since (d/history db) last-read-chat-db)
          messages (query/all db db-history query (client.chat/datomic-chat-entity-query store))

          sulo-db (db/db (:conn sulo-datomic))
          user-data (db/pull-all-with sulo-db
                                      (client.chat/focus-chat-message-user-query query)
                                      {:where   '[[$chat ?chat :chat/store ?store-id]
                                                  [$db-hist ?chat :chat/messages ?msgs]
                                                  [$chat ?msgs :chat.message/user ?e]
                                                  [$ ?e :user/profile]]
                                       :symbols {'?store-id (:db/id store)
                                                 '$chat     db
                                                 '$db-hist  db-history}})]
      {:sulo-db-tx user-data
       :chat-db-tx messages}))
  (last-read [this]
    (d/basis-t (chat-db this)))
  (chat-update-stream [this]
    (:store-id-chan this))
  (sync-up-to! [this basis-t]
    (when basis-t
      (debug "Syncing chat up to basis-t: " basis-t)
      (deref (d/sync (:conn chat-datomic) basis-t) 1000 nil))))

;; ### Datomic implementation END
