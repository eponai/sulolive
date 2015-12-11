(ns eponai.client.parser
  (:require [om.next :as om]
            [datascript.core :as d]))

(defmulti read om/dispatch)
(defmulti mutate om/dispatch)

(defn debug-read [{:keys [query] :as env} k params]
  (prn "Reading: " {:key k :params params :query query})
  (read env k params))

(defmethod read :default
  [_ k _]
  (prn "WARN: Returning nil for read key: " k))

;; Proxies a component's query
;; TODO: Create more proxies if a component wants to proxy more than one? Or figure out how to proxy many.
(defmethod read :proxy
  [{:keys [parser query] :as env} _ _]
  {:value (parser (dissoc env :query) query)})

(defn pull-all
  "takes the database, a pull query and where-clauses, where the where-clauses
  return some entity ?e."
  [state query where-clauses]
  (d/q (vec (concat '[:find [(pull ?e ?query) ...]
                         :in $ ?query
                         :where]
                       where-clauses))
          (d/db state)
       query))

(defmethod mutate 'datascript/transact
  [{:keys [state]} _ {:keys [txs]}]
  {:action #(d/transact! state txs)})

(defn cas! [component id key old-value new-value]
  (om/transact! component
                `[(datascript/transact
                    {:txs [[:db.fn/cas ~id ~key ~old-value ~new-value]]})]))
