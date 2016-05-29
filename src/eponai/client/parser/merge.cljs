(ns eponai.client.parser.merge
  (:require [datascript.core :as d]
            [datascript.db :as db]
            [eponai.web.homeless :as homeless]
            [eponai.client.utils :as utils]
            [eponai.common.datascript :as common.datascript]
            [om.next :as om]
            [taoensso.timbre :refer-macros [info debug error trace warn]]))

(defn transact [db tx]
  (if (empty? tx)
    db
    (let [tx (if (sequential? tx) tx [tx])]
      (debug "transacting: " tx)
      (d/db-with db tx))))

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

          optimistic-tx-datoms (db/-search db [nil nil nil tx-time])

          optimistic-tx-inverse
          (when tx-time
            (->> (into []
                       (comp (remove (fn [[e]] (= e tx-entity)))
                             (map #(update % :added not))
                             (map (fn [[e a v _ added]] [(if added :db/add :db/retract) e a v])))
                       optimistic-tx-datoms)
                 (cons {:tx/mutation-uuid mutation-uuid :tx/reverted true})))

          ;; When there's an optimistic edit on a transaction, mark the transaction
          ;; so we can find it later. (make this generic when there are other entities we need to mark).
          tx-entity
          (some (fn [[e]] (let [entity (d/entity db e)]
                            (when (:transaction/uuid entity)
                              entity)))
                (utils/distinct-by :e optimistic-tx-datoms))

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
      (cond-> db
              (seq tx)
              (d/db-with tx)
              (some? tx-entity)
              (as-> $db (let [tx-uuid (:transaction/uuid tx-entity)
                              ;; This is sort of messed up, but here's the explaination:
                              ;; When we're creating new transactions, we'll have the uuid
                              ;; in $db (the new one). When we're editing transactions
                              ;; we won't get the db/id by transaction/uuid, but we'll
                              ;; have the correct db/id in the old transaction. Gah.
                              id (:db/id (or (d/entity $db [:transaction/uuid tx-uuid])
                                             (d/entity db [:transaction/uuid tx-uuid])))]
                          (assert (and tx-uuid id)
                                  (str "No uuid or db/id. Was: " tx-uuid " and " id " for tx-entity " (into {} tx-entity)))
                          (debug "Marking transaction entity with id: " id " and uuid: " tx-uuid)
                          (d/db-with $db (common.datascript/mark-entity-txs id :transaction/uuid tx-uuid))))))))

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

(defn merge-transactions [db _ {:keys [transactions conversions refs]}]
  (-> db
      (transact refs)
      ;;(transact conversions)
      ;;(transact transactions)
      ))

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

(defn deep-merge-fn [a b]
  (if (map? a)
    (merge-with deep-merge-fn a b)
    b))

(defn merge-meta [db novelty-meta]
  {:post [(db/db? %)]}
  (let [basis-t-novelty (:eponai.common.parser/read-basis-t novelty-meta)
        basis-t-entity (->> (d/entity db [:db/ident :eponai.common.parser/read-basis-t])
                            (into {:db/ident :eponai.common.parser/read-basis-t}))]
    (transact db (merge-with deep-merge-fn basis-t-entity basis-t-novelty))))

(defn merge!
  "Takes a merge-fn which is passed [db key params] and
  should return db. Hook for specific platforms to do
  arbitrary actions by key.
  Returns merge function for om.next's reconciler's :merge"
  [merge-fn]
  (fn [reconciler db novelty]
    (debug "Merge! transacting novelty:" novelty)
    (let [merged-novelty (merge-novelty-by-key merge-fn db (:result novelty))
          db-with-meta (merge-meta (:next merged-novelty) (:meta novelty))
          ks (vec (:keys merged-novelty))]
      (debug "Merge! returning keys:" (:keys merged-novelty))
      {:next db-with-meta
       :keys (cond-> ks (empty? ks) (conj :om.next/skip))})))
