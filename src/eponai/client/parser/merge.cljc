(ns eponai.client.parser.merge
  (:require [datascript.core :as d]
            [datascript.db]
            [eponai.common.search :as common.search]
            [eponai.common.database :as db]
            [eponai.common.parser :as parser]
            [eponai.common.parser.util :as parser.util]
            [eponai.client.chat :as client.chat]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [info debug error trace warn]]
            [eponai.client.auth :as auth]
            [cemerick.url :as url]
            [datascript.core :as datascript]
            [clojure.string :as str]))

(defn db-with [db tx]
  (if (empty? tx)
    db
    (let [tx (if (sequential? tx) tx [tx])]
      (d/db-with db tx))))

(defn merge-error [db key val]
  (error "error on key:" key "error:" val ". Doing nothing with it.")
  db)

(defn merge-mutation-message [db history-id key val message-type]
  (let [message (get-in val [::parser/mutation-message message-type])]
    (debug "Mutation message-type" message-type "for :" key " message: " message)
    (parser/store-message db history-id (parser/->message-from-server key message message-type))))

(defn merge-signout [db key val]
  ;;TODO: We probably need to do more retracting.
  (letfn [(all-entities-with-attr [attr]
            (into []
                  (map :e)
                  (d/datoms db :aevt attr)))]
    (db-with db (into [] (comp (mapcat all-entities-with-attr)
                               (map #(vector :db.fn/retractEntity %)))
                      [::parser/read-basis-t-graph]))))

(defmulti client-merge (fn [db k v] k))
(defmethod client-merge :default
  [db k val]
  (db-with db val))

(defmethod client-merge :datascript/schema
  [db _ datascript-schema]
  (let [current-schema (:schema db)
        current-entities (d/q '[:find [(pull ?e [*]) ...] :where [?e]] db)
        new-schema (merge-with merge current-schema datascript-schema)
        new-conn (d/create-conn new-schema)]
    (d/transact new-conn current-entities)
    (d/db new-conn)))

(defmethod client-merge :query/auth
  [db _ val]
  (if (some? (:db/id val))
    (db-with db [val
                  [:db/add [:ui/singleton :ui.singleton/auth] :ui.singleton.auth/user (:db/id val)]])
    db))

(defmethod client-merge :query/locations
  [db k val]
  (if (not-empty val)
    (db-with db [[:db/add [:ui/singleton :ui.singleton/auth] :ui.singleton.auth/locations val]])
    db))

(defmethod client-merge :query/product-search
  [db k val]
  (letfn [(set-search-db [search-db]
            (db-with db [[:db/add [:ui/singleton :ui.singleton/product-search] :ui.singleton.product-search/db search-db]]))]
    (if (db/database? val)
      (set-search-db val)
      (if-some [new-search-db (some-> db
                                      (db/singleton-value :ui.singleton.product-search/db)
                                      (common.search/transact val))]
        (set-search-db new-search-db)
        (do
          (debug "No new search db with merge value: " val)
          db)))))

(defn- get-chat-db [db]
  {:pre [(:schema db)]}
  (if-some [chat-db (db/singleton-value db :ui.singleton.chat-config/chat-db)]
    chat-db
    (db/db (datascript/create-conn (:schema db)))))

(defn- placeholder-refs
  "Returns transactions that adds {:placeholder :workaround} for every entity that has attr."
  [db attr]
  (sequence
    (comp (map :v)
          (distinct)
          (remove (fn [user-id]
                    (seq (db/datoms db :eavt user-id :placeholder :workaround))))
          (map (fn [user-id]
                 {:db/id user-id :placeholder :workaround})))
    (db/datoms db :aevt attr)))

(defmethod client-merge :query/chat
  [db k {:keys [sulo-db-tx chat-db-tx]}]
  (let [chat-db (-> (get-chat-db db)
                    (db-with chat-db-tx)
                    (client.chat/trim-chat-messages client.chat/message-limit))
        ;; datascript can't have refs without having the ref in the same database. Lame.
        ;; so we put an entity with the same db/id in the database.
        ;; Pretty sure it's this line:
        ;; https://github.com/tonsky/datascript/blob/master/src/datascript/pull_api.cljc#L155
        chat-db (db-with chat-db (placeholder-refs chat-db :chat.message/user))
        db (db-with db sulo-db-tx)]
    (db-with db [{:ui/singleton                      :ui.singleton/chat-config
                   :ui.singleton.chat-config/chat-db chat-db}])))

;;;;;;; API

(defn merge-mutation [merge-fn db history-id key val]
  {:pre  [(or (nil? merge-fn) (methods merge-fn))
          (datascript.db/db? db)
          (symbol? key)]
   :post [(datascript.db/db? %)]}

  ;; Passing db to all functions for mutate.
  ;; We may want to do this for merge-read also.
  (cond-> db
          (when merge-fn (contains? (methods merge-fn) key))
          (merge-fn key val)

          (some? (get-in val [:om.next/error ::parser/mutation-message]))
          (merge-mutation-message history-id key (:om.next/error val) ::parser/error-message)

          (some? (get-in val [:result ::parser/mutation-message]))
          (merge-mutation-message history-id key (:result val) ::parser/success-message)))

(defn merge-read [merge-fn db key val]
  {:pre  [(or (nil? merge-fn) (methods merge-fn)) (datascript.db/db? db)]
   :post [(datascript.db/db? %)]}

  ;; Dispatch proxy first, so our merge-fn doesn't have to
  ;; implement that themselves.
  (if (#{"proxy" "routing"} (namespace key))
    (reduce-kv (fn [db k v] (merge-read merge-fn db k v))
               db
               val)
    (cond
      (when merge-fn (contains? (methods merge-fn) key))
      (merge-fn db key val)

      (or (= key :om.next/error)
          (some? (:om.next/error val)))
      (merge-error db key val)

      :else
      (client-merge db key val))))

;; Returns a seq of keys to dispatch re-reads from.
;; Enables us to return different keys than the one queried.
(defmulti multiply-key (fn [k] k))
(defmethod multiply-key :default [_] [])

;; If we query for :query/skus, we want to re-read :query/cart.
(defmethod multiply-key :query/skus [_] [:query/cart])


(defn merge-novelty-by-key
  "Merges server response for each [k v] in novelty. Returns the next db and the keys to re-read."
  [merge-fn db novelty history-id]
  {:pre  [(or (nil? merge-fn) (methods merge-fn)) (datascript.db/db? db)]
   :post [(:keys %) (datascript.db/db? (:next %))]}
  ;; Merge :datascript/schema first if it exists
  (let [keys-to-merge-first [:datascript/schema]
        prio-novelty (select-keys novelty keys-to-merge-first)
        other-novelty (apply dissoc novelty keys-to-merge-first)
        ordered-novelty (concat prio-novelty other-novelty)]
    (reduce
      (fn [{:keys [next] :as m} [key value]]
        #?(:cljs (debug "Merging response for key:" key "value:" value))
        (if (symbol? key)
          (do (assert (some? history-id)
                      (str "No history-id was provided when merging mutations for novelty: " ordered-novelty))
              (-> m
                  (assoc :next (merge-mutation merge-fn next history-id key value))
                  (update :keys conj :query/messages)))
          (-> m
              (assoc :next (merge-read merge-fn next key value))
              (update :keys into
                      (mapcat (fn [k] (cons k (multiply-key k))))
                      (if (parser/is-special-key? key)
                        (map (fn [[k _]] k) value)
                        [key])))))
      {:keys [] :next db}
      ordered-novelty)))

(defn merge-meta [db novelty-meta]
  {:post [(datascript.db/db? %)]}
  (let [new-graph (some-> (::parser/read-basis-t novelty-meta)
                          (parser.util/graph-read-at-basis-t true))
        old-graph (::parser/read-basis-t-graph
                    (d/entity db [:ui/singleton ::parser/read-basis-t]))
        merged-graph (parser.util/merge-graphs old-graph new-graph)]
    (cond-> db
            (not= old-graph merged-graph)
            (db-with {:ui/singleton                ::parser/read-basis-t
                       ::parser/read-basis-t-graph merged-graph}))))

(defn- logout-with-redirect! [auth]
  #?(:cljs (-> (url/url (str js/window.location.origin "/logout"))
               (assoc :query {:redirect (str js/window.location.pathname)})
               (str)
               (js/window.location.replace))
     :clj  (throw (ex-info "jvm clients cannot handle logout at this time." {:auth-map auth}))))

(defn handle-auth-response! [reconciler auth]
  (let [{:keys [redirects prompt-login unauthorized logout]} auth]
    (when logout
      (debug "LOGGING OUT because of auth: " auth)
      (logout-with-redirect! auth))
    (when unauthorized
      (#?(:cljs js/alert :clj error) "You are unauthorized to execute the action"))
    (when prompt-login
      (auth/show-lock (-> reconciler :config :shared :shared/auth-lock)))
    (when (seq redirects)
      (throw (ex-info "Unable to handle redirects at this time." {:redirects redirects})))))

(defn merge!
  "Takes a merge-fn which is passed [db key params] and
  should return db. Hook for specific platforms to do
  arbitrary actions by key.
  Returns merge function for om.next's reconciler's :merge"
  [& [merge-fn]]
  (fn [reconciler current-db {:keys [db result meta history-id auth] :as novelty} query]
    #?(:cljs (debug "Merge! transacting novelty:" (update novelty :db :max-tx)))
    (let [db (cond-> db (some? meta) (merge-meta meta))
          ret (if result
                (merge-novelty-by-key merge-fn db result history-id)
                ;; TODO: What keys can we pass to force re-render?
                {:next db :keys []})]
      (when (seq auth)
        (handle-auth-response! reconciler auth))
      #?(:cljs (debug "Merge! returning keys:" (:keys ret) " with db: " (-> ret :next :max-tx)))
      ret)))
