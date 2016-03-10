(ns eponai.client.parser.read
  (:require [datascript.core :as d]
            [eponai.common.database.pull :as p]
            [eponai.common.parser.read :refer [read]]
            [eponai.common.parser.util :as parser.util]
            [taoensso.timbre :refer-macros [debug]]))

(defn read-entity-by-key
  "Gets an entity by it's ref id. Returns the full component unless a pull pattern is supplied.

  Examples:
  om/IQuery
  (query [{[:ui/component :ui.component/transactions] [:db/id]}])
  om/IQuery
  (query [[:ui/singleton :ui.singleton/budget]]"
  [db query key]
  (let [e (d/entity db key)]
    {:value (cond
              (nil? e) e
              query (p/pull db query (:db/id e))
              :else (d/touch e))}))

;; -------- Readers for UI components

(defmethod read :query/modal
  [{:keys [db query]} _ _]
  {:value (first (p/pull-many db query (p/all-with db {:where [['?e :ui/singleton :ui.singleton/modal]]})))})

(defmethod read :query/menu
  [{:keys [db query]} _ _]
  {:value (first (p/pull-many db query (p/all-with db {:where [['?e :ui/singleton :ui.singleton/menu]]})))})

(defmethod read :query/loader
  [{:keys [db query]} _ _]
  {:value (first (p/pull-many db query (p/all-with db {:where [['?e :ui/singleton :ui.singleton/loader]]})))})

(defmethod read :query/budget
  [{:keys [db query]} _ _]
  {:value (p/pull db query [:ui/singleton :ui.singleton/budget])})

(defmethod read :query/selected-transaction
  [{:keys [db query]} _ _]
  (read-entity-by-key db query [:ui/component :ui.component/transactions]))

(defmethod read :ui/component
  [{:keys [db ast query]} _ _]
  (read-entity-by-key db query (:key ast)))

(defmethod read :ui/singleton
  [{:keys [db ast query]} _ _]
  (read-entity-by-key db query (:key ast)))

;; --------------- Remote readers ---------------

(def query-local-transactions
  (parser.util/cache-last-read
    (fn
      [{:keys [db query]} _ p]
      {:value (let [ret (p/pull-many db query (p/find-transactions db p))]
                (debug "Found transactions: " (count ret) ": " ret)
                ret)})))

(defmethod read :query/transactions
  [{:keys [db target ast] :as env} k {:keys [filter]}]
  (let [budget (-> (d/entity db [:ui/component :ui.component/budget]) :ui.component.budget/uuid)]
    (if (= target :remote)
      ;; Pass the active budget uuid to remote reader
      {:remote (assoc-in ast [:params :budget-uuid] budget)}

      ;; Local read
      (query-local-transactions env k {:filter filter
                                       :budget-uuid budget}))))

(defmethod read :query/dashboard
  [{:keys [db ast query target]} _ _]
  (let [budget-uuid (-> (d/entity db [:ui/component :ui.component/budget])
                        :ui.component.budget/uuid)]
    (if (= target :remote)
      ;; Pass the active budget uuid to remote reader
      {:remote (assoc-in ast [:params :budget-uuid] budget-uuid)}

      ;; Local read
      (let [eid (if budget-uuid
                  (p/one-with db (p/budget-with-uuid budget-uuid))

                  ;; No budget-uuid, grabbing the one with the smallest created-at
                  (p/min-by db :budget/created-at (p/budget)))]

        {:value (when eid
                  (p/pull db query (p/one-with db {:where [['?e :dashboard/budget eid]]})))}))))