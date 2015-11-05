(ns flipmunks.budget.parse
  (:require [om.next :as om]
            [datascript.core :as d]))

(defmulti read om/dispatch)
(defmulti mutate om/dispatch)

(defmethod read :default
  [_ k _]
  (prn "tried reading key: " k))

(defn pull-all
  "takes the database, a pull selector and where-clauses, where the where-clauses
  return some entity ?e."
  [state selector where-clauses]
  (d/q (vec (concat '[:find [(pull ?e ?selector) ...]
                         :in $ ?selector
                         :where]
                       where-clauses))
          (d/db state)
          selector))

(defmethod mutate 'datascript/transact
  [{:keys [state]} _ {:keys [txs]}]
  {:value []
   :action #(d/transact! state txs)})
