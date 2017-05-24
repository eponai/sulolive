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
            [eponai.server.external.datomic :as datomic]))

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

(defn- parse-chat-message-user-query*
  "Gets the pattern for :chat.message/user from a :query/chat pattern"
  [query]
  (letfn [(subquery-for-key [query key]
            (let [query-parser (om/parser {:mutate (constantly nil)
                                           :read   (fn [env k p]
                                                     (when (= k key)
                                                       {:value (:query env)}))})]
              (-> (query-parser {} query)
                  (get key))))]
    (-> query
        (subquery-for-key :chat/messages)
        (subquery-for-key :chat.message/user))))

(def parse-chat-message-user-query
  (memoize parse-chat-message-user-query*))

(defn chat-db [chat]
  (d/db (:conn (:chat-datomic chat))))

(defrecord DatomicChat [sulo-datomic chat-datomic]
  component/Lifecycle
  (start [this]
    (if (::started? this)
      this
      (let [store-id-chan (async/chan (async/sliding-buffer 1000))
            control-chan (async/chan)
            tx-report-queue (d/tx-report-queue (:conn chat-datomic))]
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
                    :tx-report-queue tx-report-queue
                    :control-chan control-chan
                    :store-id-chan store-id-chan))))
  (stop [this]
    (when (::started? this)
      (d/remove-tx-report-queue (:conn chat-datomic))
      ;; Adding ::finished to will eventually close the control-chan
      (.add (:tx-report-queue this) ::finished)
      (let [[v c] (async/alts!! [(:control-chan this) (async/timeout 1000)])]
        (if (= c (:control-chan this))
          (debug "DatomicChat successfully stopped.")
          (debug "DatomicChat timedout when stopping.")))
      ;; Closing :store-id-chan once we've closed the tx-report stuff.
      (async/close! (:store-id-chan this)))
    (dissoc this ::started? :tx-report-queue :control-chan :store-id-chan))

  IWriteStoreChat
  (write-message [this store user message]
    (let [tx (format/chat-message (chat-db this) store user message)]
      (db/transact (:conn chat-datomic) tx)))

  IReadStoreChat
  (initial-read [this store query]
    (let [chat-messages (db/pull-one-with (chat-db this)
                                          query
                                          (datomic-chat-entity-query store))
          users (into #{} (comp (mapcat :chat/messages)
                                (map :chat.message/user)
                                (map :db/id))
                      [chat-messages])
          user-pattern (parse-chat-message-user-query query)
          _ (debug "Parsed user pattern: " user-pattern)
          user-data (db/pull-many (db/db (:conn sulo-datomic))
                                  user-pattern
                                  (seq users))]
      (conj (or user-data []) chat-messages)))
  (read-messages [this store query last-read]
    (let [db (chat-db this)
          db-history (d/since (d/history db) last-read)
          messages (query/all db db-history query (datomic-chat-entity-query store))
          user-data (->> (db/find-with db {:find    '[[?user ...]]
                                           :where   '[[$ ?chat :chat/store ?store-id]
                                                      [$db-hist ?chat :chat/messages ?msgs]
                                                      [$ ?msgs :chat.message/user ?user]]
                                           :symbols {'?store-id (:db/id store)
                                                     '$chat     db
                                                     '$db-hist  db-history}})

                         (db/pull-many (db/db (:conn sulo-datomic))
                                       (parse-chat-message-user-query query)))]
      (if (> (count messages) (count user-data))
        (into messages user-data)
        (into user-data messages))))
  (last-read [this]
    (d/basis-t (chat-db this)))
  (chat-update-stream [this]
    (:store-id-chan this))
  (sync-up-to! [this basis-t]
    (when basis-t
      (debug "Syncing chat up to basis-t: " basis-t)
      (deref (d/sync (:conn chat-datomic) basis-t) 1000 nil))))

;; ### Datomic implementation END
