(ns eponai.client.chat
  (:require
    [datascript.core :as datascript]
    [eponai.client.routes :as routes]
    [clojure.data :as data]
    [eponai.common]
    [eponai.common.database :as db]
    [eponai.common.parser :as parser]
    [eponai.common.parser.util :as parser.util]
    [taoensso.timbre :refer [warn debug error]]
    [eponai.client.auth :as client.auth]))

(def message-time-limit (* 3600 1000))

(defprotocol IStoreChatListener
  (start-listening! [this store-id])
  (stop-listening! [this store-id]))

;; Puts the pattern here because it's important that we get exactly the same pattern.
;; The reasons is we use :query-hash in the read-basis-t-graph.
(def query-chat-pattern [:chat/store
                         ;; ex chat modes: :chat.mode/public :chat.mode/sub-only :chat.mode/fb-authed :chat.mode/owner-only
                         :chat/modes
                         {:chat/messages [:chat.message/client-side-message?
                                          :chat.message/id
                                          {:chat.message/user [:user/email
                                                               :db/id
                                                               {:user/profile [{:user.profile/photo [:photo/id]}
                                                                               :user.profile/name]}]}
                                          :chat.message/text
                                          :chat.message/created-at]}])

(defn- read-basis-t-graph [db]
  (::parser/read-basis-t-graph
    (db/entity db [:ui/singleton ::parser/read-basis-t])))

(defn chat-basis-t [db store-id]
  (try
    (some-> (read-basis-t-graph db)
            (parser.util/get-basis-t :query/chat {:locations (:sulo-locality/path (client.auth/current-locality db))
                                                  :store-id  store-id})
            ;; query/chat has a map of last reads because of legacy reasons. (datomic-chat).
            (:last-read-chat-db))
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
  (let [db (db/to-db x)]
    (-> (routes/current-route db)
        (:route-params)
        (select-keys [:store-id])
        (routes/normalize-route-params db)
        (:store-id))))

;; ###########
;; ## Query

;; Messages stored on the client
(def message-limit 50)

(def focus-chat-message-user-query
  (memoize (fn [query]
             (parser.util/focus-subquery query [:chat/messages :chat.message/user]))))

(def focus-chat-message-query
  (memoize (fn [query]
             (parser.util/focus-subquery query [:chat/messages]))))

(defn datomic-chat-entity-query [store-id]
  {:where   '[[?e :chat/store ?store-id]]
   :symbols {'?store-id store-id}})

(defn- read-chat-messages [chat-db sulo-db query chat-id limit]
  (let [chat-messages (if (nil? limit)
                        (mapv :v (db/datoms chat-db :eavt chat-id :chat/messages))
                        (into []
                              (comp (map :v) (take limit))
                              (sort-by :tx
                                       #(compare %2 %1)
                                       (db/datoms chat-db :eavt chat-id :chat/messages))))
        users (transduce
                (comp (map #(db/entity chat-db %))
                      (map :chat.message/user)
                      (map (juxt :db/id identity)))
                (completing (fn [m [id e]]
                              (update m id (fnil conj []) (into {:db/id id} e))))
                {}
                chat-messages)

        pulled-messages (db/pull-many chat-db (focus-chat-message-query query) chat-messages)
        pulled-chat (db/pull chat-db (parser.util/remove-query-key :chat/messages query) chat-id)
        ;; TODO: Get :chat/modes from chat-db or sulo-db.
        user-pattern (focus-chat-message-user-query query)
        user-data (db/pull-many sulo-db user-pattern (seq (keys users)))]
    {:sulo-db-tx user-data
     :chat-db-tx (assoc pulled-chat :chat/messages pulled-messages)}))

(defn read-chat [chat-db sulo-db query store limit]
  (if-let [chat-id (db/one-with chat-db (datomic-chat-entity-query (:db/id store)))]
    (read-chat-messages chat-db sulo-db query chat-id limit)
    {:sulo-db-tx []
     :chat-db-tx {}}))

;; #######################
;; ## DB management

(defn trim-chat-messages [chat-db chat-limit]
  (let [chat-datoms (into []
                          (remove (fn [[e]]
                                    (contains? (db/entity chat-db e) :chat.message/client-side-message?)))
                          (db/datoms chat-db :aevt :chat.message/text))
        trim-messages (fn [db limit]
                        (datascript/db-with
                          db
                          (sequence
                            (comp
                              (take limit)
                              (mapcat (fn [[e]]
                                        (db/checked-retract-entity db e :chat/messages))))
                            (sort-by :tx chat-datoms))))
        messages (count chat-datoms)]
    (cond-> chat-db
            (> messages chat-limit)
            (trim-messages (- messages chat-limit)))))

(defn get-chat-db [db]
  (db/singleton-value db :ui.singleton.chat-config/chat-db))

(defn get-or-create-chat-db [db]
  {:pre [(:schema db)]}
  (if-some [chat-db (get-chat-db db)]
    chat-db
    (db/db (datascript/create-conn (:schema db)))))

(defn set-chat-db-tx [chat-db]
  [{:ui/singleton                     :ui.singleton/chat-config
    :ui.singleton.chat-config/chat-db chat-db}])
