(ns eponai.client.parser.merge
  (:require [datascript.core :as d]
            [datascript.db :as db]
            [eponai.client.homeless :as homeless]
            [taoensso.timbre :refer-macros [info debug error trace warn]]))

(defn transact [conn tx]
  (let [tx (if (sequential? tx) tx [tx])]
    (debug "transacting: " tx)
    (d/transact! conn tx)))

(defn- error-popup-window! [k value]
  ;; when there's an error and it's of text/html content type,
  ;; this means we're hopefully in development and we'll
  ;; show the error in a popup.
  (when-let [{:keys [body headers]} (or (when (= k :om.next/error) value)
                                        (:om.next/error value))]
    (when (and body (homeless/content-type? headers))
      (homeless/popup-window-with-body body))))

(defn merge-error [env key val]
  (error-popup-window! key val)
  ;; TODO: Alert the user some how?
  (error "error on key:" key "error:" val ". Doing nothing with it."))

(defn sync-optimistic-tx
  [env k {:keys [result]}]
  (when-let [mutation-uuid (:mutation-uuid result)]
    (let [db (d/db (:state env))
          tx-time (d/q '{:find  [?tx .]
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
            (->> (db/-search (d/db (:state env)) [nil nil nil tx-time])
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
      (when tx
        (debug "Syncing optimistic transaction: " k " with transactions:" tx)
        (d/transact! (:state env) tx)))))

(defn merge-schema [{:keys [state]} _ datascript-schema]
  (let [current-schema (:schema @state)
        current-entities (d/q '[:find [(pull ?e [*]) ...] :where [?e]] (d/db state))
        new-schema (merge-with merge current-schema datascript-schema)
        new-conn (d/create-conn new-schema)]
    (d/transact new-conn current-entities)
    (d/reset-conn! state @new-conn)))

;;;;;;; API

(defn merge-mutation! [env key val]
  (cond
    (some? (-> val :result :mutation-uuid))
    (sync-optimistic-tx env key val)

    (some? (-> val :om.next/error))
    (merge-error env key val)

    :else
    (when-let [res (:result val)]
      (when (seq res)
        (transact (:state env) res)))))

(defn merge-read! [env key val]
  (cond
    (or (= key :om.next/error)
        (some? (:om.next/error val)))
    (merge-error env key val)

    (= "proxy" (namespace key))
    (doseq [[k v] val]
      (merge-read! env k v))

    (= :datascript/schema key)
    (merge-schema env key val)

    :else
    (transact (:state env) val)))
