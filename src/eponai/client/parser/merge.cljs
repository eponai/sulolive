(ns eponai.client.parser.merge
  (:require [datascript.core :as d]
            [datascript.db :as db]
            [eponai.web.homeless :as homeless]
            [taoensso.timbre :refer-macros [info debug error trace warn]]))

(defn transact [db tx]
  (let [tx (if (sequential? tx) tx [tx])]
    (debug "transacting: " tx)
    (d/db-with db tx)))

(defn- error-popup-window! [k value]
  ;; when there's an error and it's of text/html content type,
  ;; this means we're hopefully in development and we'll
  ;; show the error in a popup.
  (when-let [{:keys [body headers]} (or (when (= k :om.next/error) value)
                                        (:om.next/error value))]
    (when (and body (homeless/content-type? headers))
      (homeless/popup-window-with-body body))))

(defn merge-error [db key val]
  (error-popup-window! key val)
  ;; TODO: Alert the user some how?
  (error "error on key:" key "error:" val ". Doing nothing with it.")
  db)

(defn sync-optimistic-tx
  [db k {:keys [result]}]
  (when-let [mutation-uuid (:mutation-uuid result)]
    (let [[tx-entity tx-time] (first (d/q '{:find  [?e ?tx]
                                            :in    [$ ?uuid]
                                            :where [[?e :tx/mutation-uuid ?uuid ?tx]
                                                    [?e :tx/reverted ?reverted]
                                                    [(not ?reverted)]]}
                                          db
                                          mutation-uuid))
          _ (when-not tx-time
              (debug "No tx-time found. Did the datascript transaction actually happen in your optimistic mutation?")
              (let [tx (d/touch (d/entity db [:tx/mutation-uuid mutation-uuid]))]
                (if (true? (:tx/reverted tx))
                  (debug "Had already reverted optimistic transaction: " tx
                         " will still transact the real transactions, because why not?")
                  (warn "Optimistic transaction:" tx
                        " had no tx-time..? What is this."))))

          optimistic-tx-inverse
          (when tx-time
            (->> (into []
                       (comp (filter (fn [[e]] (not= e tx-entity)))
                             (map #(update % :added not))
                             (map (fn [[e a v _ added]] [(if added :db/add :db/retract) e a v])))
                       (db/-search db [nil nil nil tx-time]))
                 (cons {:tx/mutation-uuid mutation-uuid :tx/reverted true})))

          real-tx
          (into [] (comp
                     ;; Remove the remote :tx/mutation-uuid datom, since we've got our own
                     (filter (fn [[_ a]] (not= a :tx/mutation-uuid)))
                     ;; Does not include the ?tx time because we're not quite sure how
                     ;; that will affect things.
                     (map (fn [[e a v _ added]] [(if added :db/add :db/retract) e a v])))
                (:datoms result))

          tx (concat optimistic-tx-inverse real-tx)]
      (debug "Syncing optimistic transaction: " k " with transactions:" tx)
      (if tx
        (d/db-with db tx)
        db))))

(defn merge-schema [db _ datascript-schema]
  (let [current-schema (:schema db)
        current-entities (d/q '[:find [(pull ?e [*]) ...] :where [?e]] db)
        new-schema (merge-with merge current-schema datascript-schema)
        new-conn (d/create-conn new-schema)]
    (d/transact new-conn current-entities)
    (d/db new-conn)))

(defn merge-current-user [db _ current-user]
  (d/db-with db [{:ui/singleton :ui.singleton/auth
                  :ui.singleton.auth/user current-user}]))

(defn merge-transactions [db _ {:keys [transactions conversions]}]
  (let [txs (concat transactions conversions)]
    (if (seq txs)
      (d/db-with db (concat transactions conversions))
      db)))

;;;;;;; API

(defn merge-mutation [merge-fn db key val]
  {:pre [(methods merge-fn) (db/db? db)]
   :post [(db/db? %)]}
  ;; Try to pass it to the merge function first
  ;; Otherwise, just do the default.
  (cond
    (contains? (methods merge-fn) key)
    (merge-fn db key val)

    (some? (-> val :result :mutation-uuid))
    (sync-optimistic-tx db key val)

    (some? (-> val :om.next/error))
    (merge-error db key val)

    (-> val :result seq)
    (transact db (:result val))

    :else
    (do
      (debug "Nothing to merge for key: " key " value: " val)
      db)))

;; TODO: Fix comments in this namespace.
;; TODO: Clean up merge-read and merge-mutate now that we have a merge-fn.
(defn merge-read [merge-fn db key val]
  {:pre [(methods merge-fn) (db/db? db)]
   :post [(db/db? %)]}

  ;; Dispatch proxy first, so our merge-fn doesn't have to
  ;; implement that themselves.
  (if (= "proxy" (namespace key))
    (reduce-kv (fn [db k v] (merge-read merge-fn db k v))
               db
               val)
    (cond
      (contains? (methods merge-fn) key)
      (merge-fn db key val)

      (or (= key :om.next/error)
          (some? (:om.next/error val)))
      (merge-error db key val)

      (= :datascript/schema key)
      (merge-schema db key val)

      (= :query/transactions key)
      (merge-transactions db key val)

      (= :user/current key)
      (merge-current-user db key val)

      :else
      (transact db val))))

(defn split-mutations [novelty]
  (letfn [(mutation? [k] (symbol? k))]
    (reduce-kv (fn [[mutations reads] k v]
                 (if (mutation? k)
                   [(assoc mutations k v) reads]
                   [mutations (assoc reads k v)]))
               [{} {}]
               novelty)))

(defn merge-novelty-by-key
  ;; TODO: Add comment when this has stabilized.
  "Merges server response for each [k v] in novelty. Returns the next db and the keys to re-read."
  [merge-fn db novelty]
  {:pre [(methods merge-fn) (db/db? db)]
   :post [(:keys %) (db/db? (:next %))]}
  ;; Merge :datascript/schema first if it exists
  (let [keys-to-merge-first (select-keys novelty [:datascript/schema :user/current])
        other-novelty (apply dissoc novelty keys-to-merge-first)
        [mutations reads] (split-mutations other-novelty)
        ;; Merge symbols before keywords, but merge some keys first, always..?
        ordered-novelty (concat keys-to-merge-first mutations reads)]
    (reduce
      (fn [{:keys [next] :as m} [key value]]
        (debug "Merging response for key:" key "value:" value)
        (if (symbol? key)
          (assoc m :next (merge-mutation merge-fn next key value))
          ;; Unwrap keys if they are wrapped in maps with :value
          (-> m
              (assoc :next (merge-read merge-fn next key value))
              (update :keys conj key))))
      {:keys [] :next db}
      ordered-novelty)))

(defn merge-meta [db novelty-meta]
  {:post [(db/db? %)]}
  (reduce-kv (fn [db k v]
               (condp = k
                 :eponai.common.parser/read-basis-t
                 (transact db (assoc v :db/ident :eponai.common.parser/read-basis-t))))
             db
             novelty-meta))

(defn merge!
  "Takes a merge-fn which is passed [db key params] and
  should return db. Hook for specific platforms to do
  arbitrary actions by key.
  Returns merge function for om.next's reconciler's :merge"
  [merge-fn]
  (fn [reconciler db novelty]
    (debug "Merge! transacting novelty:" novelty)
    (let [merged-novelty (merge-novelty-by-key merge-fn db (:result novelty))
          db-with-meta (merge-meta (:next merged-novelty) (:meta novelty))]
      (debug "Merge! returning keys:" (:keys merged-novelty))
      {:next db-with-meta
       :keys (:keys merged-novelty)})))
