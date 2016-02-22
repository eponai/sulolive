(ns eponai.client.parser.merge
  (:require [datascript.core :as d]
            [taoensso.timbre :refer-macros [info debug error trace]]))

(defn transact [conn tx]
  (let [tx (if (sequential? tx) tx [tx])]
    (debug "transacting: " tx)
    (d/transact! conn tx)))

(defmulti merge-novelty (fn [_ k _] k))

(defmethod merge-novelty :default
  [{:keys [state] :as env} key val]
  (if-let [err (:om.next/error val)]
    (error "merge-novelty error on key:" key "error:" err)
    (cond
      ;; When we've got a proxy merge the value with the novelty
      (= "proxy" (namespace key)) (reduce-kv (fn [e k v]
                                               (do (merge-novelty e k v)
                                                   e))
                                             env
                                             val)
      ;; Mutation
      (symbol? key)
      (when-let [res (:result val)]
        (when (seq res)
          (transact state res)))

      :else
      (transact state val)))
  :dissoc)

(defmethod merge-novelty :om.next/error
  [_ key val]
  (error "merge-novelty error on key:" key "error:" val)
  :dissoc)

(defmethod merge-novelty :datascript/schema
  [{:keys [state]} _ datascript-schema]
  (let [current-schema (:schema @state)
        current-entities (d/q '[:find [(pull ?e [*]) ...] :where [?e]] (d/db state))
        new-schema (merge-with merge current-schema datascript-schema)
        new-conn (d/create-conn new-schema)]
    (d/transact new-conn current-entities)
    (d/reset-conn! state @new-conn))
  :dissoc)
