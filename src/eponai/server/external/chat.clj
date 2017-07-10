(ns eponai.server.external.chat
  (:require [datomic.api :as d]
            [eponai.common.format :as format]
            [eponai.common.database :as db]
            [eponai.server.datomic.query :as query]
            [eponai.server.external.firebase :as firebase]
            [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [taoensso.timbre :refer [debug error]]
            [eponai.server.external.datomic :as datomic]
            [eponai.client.chat :as client.chat]
            [eponai.common.format.date :as date]
            [datascript.core :as datascript])
  (:import (com.google.firebase.database DatabaseReference FirebaseDatabase$DatabaseInstances FirebaseDatabase ChildEventListener DataSnapshot DatabaseError ServerValue)
           (java.util HashMap)))

(defprotocol IWriteStoreChat
  (write-message [this store user message] "write a message from a user to a store's chat."))

(defprotocol IReadStoreChatRef
  (store-chat-reader [this sulo-db-filter] "Returns an IReadStoreChat 'value'. One with db values and not connections.")
  (sync-up-to! [this store t])
  (chat-update-stream [this] "Stream of store-id's which have new messages"))

(defprotocol IReadStoreChat
  (initial-read [this store query]
    "Initial call to get chat entity and (maybe?) some of its messages. Returns map with keys #{:sulo-db-tx :chat-db-tx}")
  (read-messages [this store query last-read]
    "Read messages from last time. Returns map with keys #{:sulo-db-tx :chat-db-tx}")
  (last-read [this] "Some identifier that can be used in read-messages to only get what's changed."))

;; #############################
;; ### Datomic implementation

(defrecord DatomicStoreChatReader [chat-db sulo-db]
  IReadStoreChat
  (initial-read [this store query]
    (client.chat/read-chat chat-db
                           sulo-db
                           query
                           store
                           client.chat/message-limit))
  (read-messages [this store query last-read]
    (let [{:keys [last-read-chat-db]} last-read
          db-history (d/since (d/history chat-db) last-read-chat-db)
          messages (query/all chat-db db-history query (client.chat/datomic-chat-entity-query (:db/id store)))

          user-data (db/pull-all-with sulo-db
                                      (client.chat/focus-chat-message-user-query query)
                                      {:where   '[[$chat ?chat :chat/store ?store-id]
                                                  [$db-hist ?chat :chat/messages ?msgs]
                                                  [$chat ?msgs :chat.message/user ?e]
                                                  [$ ?e :user/profile]]
                                       :symbols {'?store-id (:db/id store)
                                                 '$chat     chat-db
                                                 '$db-hist  db-history}})]
      {:sulo-db-tx user-data
       :chat-db-tx messages}))
  (last-read [this]
    {:last-read-chat-db (d/basis-t chat-db)
     :last-read-sulo-db (d/basis-t sulo-db)}))

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

  IReadStoreChatRef
  (store-chat-reader [this sulo-db-filter]
    (->DatomicStoreChatReader (chat-db this)
                              (cond-> (db/db (:conn sulo-datomic))
                                      (some? sulo-db-filter)
                                      (d/filter sulo-db-filter))))
  (sync-up-to! [this store basis-t]
    (when basis-t
      (debug "Syncing chat up to basis-t: " basis-t)
      (deref (d/sync (:conn chat-datomic) basis-t) 1000 nil)))
  (chat-update-stream [this]
    (:store-id-chan this)))

;; ### Datomic implementation END

;; ### Firebase implementation start

(def datascript-chat-db (db/db (datascript/create-conn
                                 {:chat/messages     {:db/valueType   :db.type/ref
                                                      :db/cardinality :db.cardinality/many}
                                  :chat/store        {:db/valueType :db.type/ref}
                                  :chat.message/user {:db/valueType :db.type/ref}
                                  :chat.message/id   {:db/unique :db.unique/identity}})))

(defn- firebase-chat-messages->datascript-messages [all-chats-ref store last-read]
  (let [values (firebase/ref->value (-> (.child all-chats-ref (str (:db/id store)))
                                        (.limitToLast client.chat/message-limit)))]
    (into []
          (comp (filter (fn [[_ m]]
                          (when-let [created-at (when (some? m)
                                                  (.get m "created-at"))]
                            (>= created-at last-read))))
                (map (fn [[k ^HashMap m]]
                       (when (some? m)
                         {:chat.message/id         k
                          :chat.message/created-at (.get m "created-at")
                          :chat.message/text       (.get m "text")
                          :chat.message/user       (.get m "user")}))))
          (when (some? values)
            (into {} values)))))

(defn firebase-store-chat-reader [all-chats-ref sulo-db]
  (let [basis-t (atom 0)
        max-created-at (fn [messages]
                         (transduce (map :chat.message/created-at) max 0 messages))]
    (reify
      IReadStoreChat
      (initial-read [this store query]
        (let [chat-messages (firebase-chat-messages->datascript-messages all-chats-ref store 0)
              _ (debug "Chat messages: " chat-messages)
              chat-db (datascript/db-with datascript-chat-db
                                          ;; Setting the chat entity's id the same as the chat/store.
                                          ;; The chat entity is unique within its own database and
                                          ;; the store is in a different database, so this is good.
                                          [{:db/id         (:db/id store)
                                            :chat/store    (:db/id store)
                                            :chat/messages chat-messages}])
              _ (reset! basis-t (max-created-at chat-messages))
              ;; Re-using everything we had before:
              ret (client.chat/read-chat chat-db
                                         sulo-db
                                         query
                                         store
                                         client.chat/message-limit)]
          (update-in ret [:chat-db-tx :chat/messages] (fn [messages]
                                                        (debug "Updating messages: " messages)
                                                        (into [] (map #(update % :chat.message/user :db/id)) messages)))))
      (read-messages [this store query last-read]
        (let [{:keys [last-read-chat-db]} last-read
              messages (firebase-chat-messages->datascript-messages all-chats-ref store last-read-chat-db)
              _ (debug "Chat messages: " messages)
              user-data (db/pull-all-with sulo-db
                                          (client.chat/focus-chat-message-user-query query)
                                          {:where   '[[?e :user/profile]]
                                           :symbols {'?store-id (:db/id store)
                                                     '[?e ...]  (map :chat.message/user messages)}})]
          (reset! basis-t (max-created-at messages))
          {:sulo-db-tx user-data
           :chat-db-tx {:db/id         (:db/id store)
                        :chat/store    (:db/id store)
                        :chat/messages messages}}))
      (last-read [this]
        {:last-read-chat-db @basis-t}))))

(defn child-listener [{:keys [on-child-added]}]
  (reify ChildEventListener
    (^void onChildAdded [_ ^DataSnapshot snapshot ^String prev]
      (on-child-added snapshot))
    (^void onChildChanged [_ ^DataSnapshot snapshot ^String prev])
    (^void onChildRemoved [_ ^DataSnapshot snapshot])
    (^void onChildMoved [_ ^DataSnapshot snapshot ^String prev])
    (^void onCancelled [_ ^DatabaseError snapshot])))

(defn store-chat-room-ref [chats-ref store-id on-child-added]
  (let [chat-room-ref (-> (.child chats-ref (str store-id))
                          (.limitToLast (* client.chat/message-limit 2)))]
    (doto chat-room-ref
      (.keepSynced true)
      (.addChildEventListener (child-listener {:on-child-added on-child-added})))))

(defrecord FirebaseStoreChat [firebase sulo-datomic]
  component/Lifecycle
  (start [this]
    (if (or (:stores this) (nil? (:database firebase)))
      this
      (let [stores-atom (atom {})
            basis-t-atom (atom 0)
            store-id-chan (async/chan (async/sliding-buffer 1000))
            chat-room-listener (fn [store-id]
                                 (fn [^DataSnapshot snapshot]
                                   (let [v (.getValue snapshot)]
                                     (when (seqable? v)
                                       (let [created-at (get (into {} v) "created-at")]
                                         (when (number? created-at)
                                           (swap! basis-t-atom max created-at))))
                                     ;; Put this new event on to the store-id-chan
                                     (async/put! store-id-chan {:event-type :store-id
                                                                :store-id   store-id
                                                                :basis-t    @basis-t-atom}))))

            all-chats-ref (.getReference ^FirebaseDatabase (:database firebase) "store-chats")
            chats-listener (fn [^DataSnapshot snapshot]
                             (let [store-id (.getKey snapshot)]
                               ;; Keep all stores refs dowloaded and stored in the stores-atom.
                               (when-not (contains? @stores-atom store-id)
                                 (locking
                                   (swap! stores-atom
                                          (fn [stores]
                                            ;; Check again in case we've already updated the store.
                                            (cond-> stores
                                                    (not (contains? stores store-id))
                                                    (assoc store-id (store-chat-room-ref all-chats-ref
                                                                                         store-id
                                                                                         (chat-room-listener store-id))))))))))]
        (.addChildEventListener all-chats-ref (child-listener {:on-child-added chats-listener}))
        (assoc this :stores stores-atom
                    :store-id-chan store-id-chan
                    :basis-t-atom basis-t-atom
                    :chats-ref all-chats-ref))))
  (stop [this]
    (dissoc this :stores :database :store-id-chan :basis-t-atom))

  IWriteStoreChat
  (write-message [this store user message]
    (let [chat-room-ref (-> (:chats-ref this)
                            (.child (str (:db/id store))))]
      (-> (.push ^DatabaseReference chat-room-ref)
          (.setValue {"user"       (:db/id user)
                      "text"       message
                      "created-at" ServerValue/TIMESTAMP}))))

  IReadStoreChatRef
  (store-chat-reader [this sulo-db-filter]
    (firebase-store-chat-reader (:chats-ref this)
                                (cond-> (db/db (:conn sulo-datomic))
                                        (some? sulo-db-filter)
                                        (d/filter sulo-db-filter))))
  (sync-up-to! [this store t]
    (when (== t (max t @(:basis-t-atom this)))
      (firebase/ref->value (-> (:chats-ref this)
                               (.child (str (:db/id store)))))))
  (chat-update-stream [this]
    (:store-id-chan this)))

(comment
  (-> [{:db/id      chat-id
        :chat/store (:db/id store)}
       {:db/id             message-id
        :chat.message/user (select-keys user [:db/id])
        :chat.message/text text
        :chat.message/created-at (date/current-millis)}
       [:db/add chat-id :chat/messages message-id]]
      (with-meta {::message-id message-id
                  ::chat-id    chat-id})))