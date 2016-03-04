(ns eponai.client.parser.merge
  (:require [datascript.core :as d]
            [datascript.db :as db]
            [eponai.client.homeless :as homeless]
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
    (let [tx-time (d/q '{:find  [?tx .]
                         :in    [$ ?uuid]
                         :where [[?e :tx/mutation-uuid ?uuid ?tx]
                                 [?e :tx/reverted ?reverted]
                                 [(not ?reverted)]]}
                       db
                       mutation-uuid)
          _ (when-not tx-time
              (let [tx (d/touch (d/entity db [:tx/mutation-uuid mutation-uuid]))]
                (if (true? (:tx/reverted tx))
                  (debug "Had already reverted optimistic transaction: " tx
                         " will still transact the real transactions, because why not?")
                  (warn "Optimistic transaction:" tx
                        " had no tx-time..? What is this."))))

          optimistic-tx-inverse
          (when tx-time
            (->> (db/-search db [nil nil nil tx-time])
                 (map #(update % :added not))
                 (map (fn [[e a v _ added]] [(if added :db/add :db/retract) e a v]))
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

;;;;;;; API

(defn merge-mutation [db key val]
  {:pre [(db/db? db)]
   :post [(db/db? %)]}
  (cond
    (some? (-> val :result :mutation-uuid))
    (sync-optimistic-tx db key val)

    (some? (-> val :om.next/error))
    (merge-error db key val)

    (-> val :result seq)
    (transact db (:result val))

    :else
    db))

;; TODO: Fix comments in this namespace.
(defn merge-read [db key val]
  {:pre [(db/db? db)]
   :post [(db/db? %)]}

  (cond
    (or (= key :om.next/error)
        (some? (:om.next/error val)))
    (merge-error db key val)

    (= "proxy" (namespace key))
    (reduce-kv (fn [db k v] (merge-read db k v))
               db
               val)

    (= :datascript/schema key)
    (merge-schema db key val)

    :else
    (transact db val)))
