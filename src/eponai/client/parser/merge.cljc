(ns eponai.client.parser.merge
  (:require [datascript.core :as d]
            [datascript.db :as db]
            [eponai.common.parser :as parser]
            [eponai.common.parser.util :as p.util]
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [info debug error trace warn]]
            [eponai.client.auth :as auth]
            [cemerick.url :as url]))

(defn transact [db tx]
  (if (empty? tx)
    db
    (let [tx (if (sequential? tx) tx [tx])]
      (d/db-with db tx))))

(defn merge-error [db key val]
  (error "error on key:" key "error:" val ". Doing nothing with it.")
  db)

(defn merge-schema [db _ datascript-schema]
  (let [current-schema (:schema db)
        current-entities (d/q '[:find [(pull ?e [*]) ...] :where [?e]] db)
        new-schema (merge-with merge current-schema datascript-schema)
        new-conn (d/create-conn new-schema)]
    (d/transact new-conn current-entities)
    (d/db new-conn)))

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
    (transact db (into [] (comp (mapcat all-entities-with-attr)
                                (map #(vector :db.fn/retractEntity %)))
                       [::parser/read-basis-t-graph]))))

(defn merge-auth [db key val]
  (if (some? (:db/id val))
    (transact db [val
                  [:db/add [:ui/singleton :ui.singleton/auth] :ui.singleton.auth/user (:db/id val)]])
    db))

;;;;;;; API

(defn merge-mutation [merge-fn db history-id key val]
  {:pre  [(or (nil? merge-fn) (methods merge-fn))
          (db/db? db)
          (symbol? key)]
   :post [(db/db? %)]}

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
  {:pre  [(or (nil? merge-fn) (methods merge-fn)) (db/db? db)]
   :post [(db/db? %)]}

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

      (= :datascript/schema key)
      (merge-schema db key val)

      (= :query/auth key)
      (merge-auth db key val)


      :else
      (transact db val))))

(defn merge-novelty-by-key
  "Merges server response for each [k v] in novelty. Returns the next db and the keys to re-read."
  [merge-fn db novelty history-id]
  {:pre  [(or (nil? merge-fn) (methods merge-fn)) (db/db? db)]
   :post [(:keys %) (db/db? (:next %))]}
  ;; Merge :datascript/schema first if it exists
  (let [keys-to-merge-first (select-keys novelty [:datascript/schema])
        other-novelty (apply dissoc novelty keys-to-merge-first)
        ordered-novelty (concat keys-to-merge-first other-novelty)]
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
              (update :keys into (if (parser/is-special-key? key)
                                   (map (fn [[k _]] k) value)
                                   [key])))))
      {:keys [] :next db}
      ordered-novelty)))

(defn merge-meta [db novelty-meta]
  {:post [(db/db? %)]}
  (let [new-graph (some-> (::parser/read-basis-t novelty-meta)
                          (p.util/graph-read-at-basis-t true))
        old-graph (::parser/read-basis-t-graph
                    (d/entity db [:ui/singleton ::parser/read-basis-t]))
        merged-graph (p.util/merge-graphs old-graph new-graph)]
    (cond-> db
            (not= old-graph merged-graph)
            (transact {:ui/singleton                            ::parser/read-basis-t
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
