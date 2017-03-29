(ns eponai.server.external.chat
  (:require [datomic.api :as d]
            [eponai.common.format :as format]
            [eponai.common.database :as db]
            [eponai.server.datomic.query :as query]
            [com.stuartsierra.component :as component]
            [suspendable.core :as suspendable]
            [clojure.core.async :as async]
            [taoensso.timbre :refer [debug error]]))

(defprotocol IWriteStoreChat
  (write-message [this store user message] "write a message from a user to a store's chat."))

(defprotocol IReadStoreChat
  (initial-read [this store query] "Initial call to get chat entity and (maybe?) some of its messages.")
  (read-messages [this store query last-read] "Read messages from last time.")
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

(defn datomic-chat-entity-query [store]
  {:where   '[[?e :chat/store ?store-id]]
   :symbols {'?store-id (:db/id store)}})

(defrecord DatomicChat [datomic]
  component/Lifecycle
  (start [this]
    (if (::started? this)
      this
      (let [conn (:conn datomic)
            store-id-chan (async/chan (async/sliding-buffer 1000))
            control-chan (async/chan)
            tx-report-queue (d/tx-report-queue conn)]
        (async/thread
          (try
            (loop []
              (let [tx-report (.take tx-report-queue)]
                (when-not (= ::finished tx-report)
                  (try
                    (doseq [store-id (updated-store-ids tx-report)]
                      (async/put! store-id-chan {:event-type :store-id
                                                 :store-id   store-id
                                                 :basis-t    (d/basis-t (:db-after tx-report))}))
                    (catch Throwable e
                      (error "Error in DatomicChat thread reading tx-report-queue: " e)
                      (async/put! store-id-chan {:exception  e
                                                 :event-type :exception})))
                  (recur))))
            (catch InterruptedException e
              (error "Error in DatomicChat thread reading tx-report-queue: " e)))
          (debug "Exiting DatomicChat async/thread..")
          (async/close! control-chan))
        (assoc this ::started? true
                    :conn conn
                    :tx-report-queue tx-report-queue
                    :control-chan control-chan
                    :store-id-chan store-id-chan))))
  (stop [this]
    (when (::started? this)
      (d/remove-tx-report-queue (:conn this))
      (.add (:tx-report-queue this) ::finished)
      (let [[v c] (async/alts!! [(:control-chan this) (async/timeout 1000)])]
        (if (= c (:control-chan this))
          (debug "DatomicChat successfully stopped.")
          (debug "DatomicChat timedout when stopping.")))
      ;; Closing :store-id-chan once we've closed the tx-report stuff.
      (async/close! (:store-id-chan this)))
    (dissoc this ::started? :conn :tx-report-queue :control-chan :store-id-chan))

  db/ConnectionApi
  (db* [this]
    (d/db (:conn this)))

  IWriteStoreChat
  (write-message [this store user message]
    (let [tx (format/chat-message (db/db this) store user message)]
      (db/transact (:conn this) tx)))

  IReadStoreChat
  (initial-read [this store query]
    (db/pull-one-with (db/db this) query (datomic-chat-entity-query store)))
  (read-messages [this store query last-read]
    (let [db (db/db this)
          db-history (d/since (d/history db) last-read)]
      (query/all db db-history query (datomic-chat-entity-query store))))
  (last-read [this]
    (d/basis-t (db/db this)))
  (chat-update-stream [this]
    (:store-id-chan this))
  (sync-up-to! [this basis-t]
    (when basis-t
      (debug "Syncing chat up to basis-t: " basis-t)
      (deref (d/sync (:conn this) basis-t) 1000 nil))))

;; ### Datomic implementation END
