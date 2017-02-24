(ns eponai.server.external.chat
  (:require [datomic.api :as d]
            [eponai.common.format :as format]
            [eponai.common.database :as db]
            [eponai.server.datomic.query :as query]))

(defprotocol IWriteStoreChat
  (write-message [this store user message] "write a message from a user to a store's chat."))

(defprotocol IReadStoreChat
  (initial-read [this store query] "Initial call to get channel and (maybe?) some of its messages.")
  (read-messages [this store query last-read] "Read messages from last time.")
  (last-read [this] "Some identifier that can be used in read-messages to only get what's changed."))

;; #############################
;; ### Datomic implementation

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
    (d/basis-t (db/db this))))

;; ### Datomic implementation END
