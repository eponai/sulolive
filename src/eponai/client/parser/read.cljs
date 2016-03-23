(ns eponai.client.parser.read
  (:require [datascript.core :as d]
            [eponai.common.database.pull :as p]
            [eponai.common.parser :refer [read]]
            [eponai.common.parser.util :as parser.util]
            [taoensso.timbre :refer-macros [debug error]]
            [eponai.common.format :as f]))

;; ################ Remote reads ####################
;; Remote reads goes here. We share these reads
;; with all client platforms (web, ios, android).
;; Local reads should be defined in:
;;     eponai.<platform>.parser.read

;; ----------

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

(defmethod read :ui/component
  [{:keys [db ast query]} _ _]
  (read-entity-by-key db query (:key ast)))

(defmethod read :ui/singleton
  [{:keys [db ast query]} _ _]
  (read-entity-by-key db query (:key ast)))

;; --------------- Remote readers ---------------

(defmethod read :datascript/schema
  [_ _ _]
  {:remote true})

(def query-local-transactions
  (parser.util/cache-last-read
    (fn
      [{:keys [db query parser] :as env} _ p]
      (let [
            tx-ids (p/find-transactions db p)
            {:keys [query/current-user]} (parser env '[{:query/current-user [:user/uuid]}])
            _ (debug "Pulled user: " current-user)
            transactions (p/txs-with-conversions db
                                                 query
                                                 {:user/uuid (:user/uuid current-user)
                                                  :tx-ids    tx-ids})]
        (debug "found transactions " transactions)
        {:value transactions}))))

(defmethod read :query/transactions
  [{:keys [db target ast] :as env} k {:keys [filter]}]
  (let [budget (-> (d/entity db [:ui/component :ui.component/budget]) :ui.component.budget/uuid)]
    (if (= target :remote)
      ;; Pass the active budget uuid to remote reader
      {:remote (assoc-in ast [:params :budget-uuid] budget)}

      ;; Local read
      (query-local-transactions env k {:filter filter
                                       :budget-uuid budget}))))

(defmethod read :query/conversions
  [_ _ _]
  {:remote true})

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
                  (when-let [dashboard-id (p/one-with db {:where [['?e :dashboard/budget eid]]})]
                    (p/pull db query dashboard-id)))}))))

(defmethod read :query/all-budgets
  [{:keys [db query]} _ _]
  {:value  (sort-by :budget/created-at (p/pull-many db query (p/all-with db (p/budget))))
   :remote true})

(defmethod read :query/all-currencies
  [{:keys [db query]} _ _]
  {:value  (p/pull-many db query (p/all-with db {:where '[[?e :currency/code]]}))
   :remote true})

(defmethod read :query/current-user
  [{:keys [db query]} _ _]
  (let [eid (p/one-with db {:where '[[?e :user/uuid]]})]
    {:value  (when eid
               (p/pull db query eid))
     :remote true}))

;; ############ Signup page reader ############

(defmethod read :query/user
  [{:keys [db query]} k {:keys [uuid]}]
  {:value  (when (and (not (= uuid '?uuid))
                      (-> db :schema :verification/uuid))
             (try
               (p/pull db query [:user/uuid (f/str->uuid uuid)])
               (catch :default e
                 (error "Error for parser's read key:" k "error:" e)
                 {:error {:cause :invalid-verification}})))
   :remote (not (= uuid '?uuid))})

(defmethod read :query/fb-user
  [{:keys [db query]} _ _]
  (let [eid (p/one-with db {:where '[[?e :fb-user/id]]})]
    {:value  (when eid
               (p/pull db query eid))
     :remote true}))