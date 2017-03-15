(ns eponai.server.external.chat
  (:require [datomic.api :as d]
            [eponai.common.format :as format]
            [eponai.common.database :as db]
            [eponai.server.datomic.query :as query]
            [clojure.core.async :as async]
            [taoensso.timbre :refer [debug error]]))

(defprotocol IWriteStoreChat
  (write-message [this store user message] "write a message from a user to a store's chat."))

(defprotocol IReadStoreChat
  (initial-read [this store query] "Initial call to get chat entity and (maybe?) some of its messages.")
  (read-messages [this store query last-read] "Read messages from last time.")
  (last-read [this] "Some identifier that can be used in read-messages to only get what's changed.")
  (chat-update-stream [this] "Stream of store-id's which have new messages"))

;; #############################
;; ### Datomic implementation

(defn- updated-store-ids [tx-report]
  (let [ret (d/q '{:find  [[?store-id ...]]
                   :where [[$ ?chat ?chat-messages-attr _]
                           [$db ?chat ?chat-store-attr ?store-id]]
                   :in    [$ $db ?chat-messages-attr ?chat-store-attr]}
                 (:tx-data tx-report)
                 (:db-after tx-report)
                 (:id (d/attribute (:db-after tx-report) :chat/messages))
                 (:id (d/attribute (:db-after tx-report) :chat/store)))]
    (debug "Found store-ids: " ret " in tx-report: " tx-report)
    ret))

(defn datomic-chat-entity-query [store]
  {:where   '[[?e :chat/store ?store-id]]
   :symbols {'?store-id (:db/id store)}})

(defrecord DatomicChat [conn]
  db/ConnectionApi
  (db* [this]
    (d/db conn))

  IWriteStoreChat
  (write-message [this store user message]
    (let [tx (format/chat-message (db/db this) store user message)]
      (db/transact conn tx)))

  IReadStoreChat
  (initial-read [this store _]
    (db/pull-one-with (db/db this) ['*] (datomic-chat-entity-query store)))
  (read-messages [this store query last-read]
    (let [db (db/db this)
          db-history (d/since (d/history db) last-read)]
      (query/all db db-history query (datomic-chat-entity-query store))))
  (last-read [this]
    (d/basis-t (db/db this)))
  (chat-update-stream [this]
    (let [tx-report-queue (d/tx-report-queue conn)
          out-chan (async/chan (async/sliding-buffer 1000))]
      ;; TODO: We need to call d/remove-tx-report-queue on this conn.
      ;; TODO: Which means we finally need to implement stuartsierra's Component lib.
      (async/thread
        (try
          (loop []
            (let [tx-report (.take tx-report-queue)]
              (try
                (doseq [store-id (updated-store-ids tx-report)]
                  (async/put! out-chan {:event-type :store-id
                                        :store-id   store-id}))
                (catch Throwable e
                  (error "Error in DatomicChat thread reading tx-report-queue: " e)
                  (async/put! out-chan {:exception  e
                                        :event-type :exception})))))
          (catch InterruptedException e
            (error "Error in DatomicChat thread reading tx-report-queue: " e))))
      out-chan)))

;; ### Datomic implementation END
