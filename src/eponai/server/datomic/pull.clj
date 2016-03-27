(ns eponai.server.datomic.pull
  (:require
    [clojure.walk :refer [walk]]
    [datomic.api :as d]
    [eponai.common.database.pull :as p]
    [eponai.common.parser.util :as parser]
    [eponai.common.report :as report]
    [taoensso.timbre :refer [debug]]))

(defn currencies [db]
  (p/q '[:find [(pull ?e [*]) ...]
       :where [?e :currency/code]] db))

(defn schema
  "Pulls schema from the db. If data is provided includes only the necessary fields for that data.
  (type/ref, cardinality/many or unique/identity)."
  [db]
  (let [schema (p/q '[:find [?e ...]
                      :where
                      [?e :db/ident ?id]
                      [:db.part/db :db.install/attribute ?e]
                      [(namespace ?id) ?ns]
                      [(.startsWith ^String ?ns "db") ?d]
                      [(not ?d)]
                      ] db)]
    (map #(into {} (d/entity db %)) schema)))

(defn widget-report-query []
  (parser/put-db-id-in-query
    [:widget/uuid
     :widget/width
     :widget/height
     {:widget/filter [{:filter/include-tags [:tag/name]}]}
     {:widget/report [:report/uuid
                      :report/group-by
                      :report/title
                      {:report/functions [:report.function/uuid
                                          :report.function/attribute
                                          :report.function/id]}]}
     :widget/index
     :widget/data
     {:widget/graph [:graph/style]}]))

(defn transaction-query []
  (parser/put-db-id-in-query
    [:transaction/uuid
     :transaction/amount
     :transaction/conversion
     {:transaction/currency [:currency/code]}
     {:transaction/tags [:tag/name]}
     {:transaction/date [:date/ymd
                         :date/timestamp]}]))

(defn widgets-with-data [{:keys [db auth] :as env} budget-eid widgets]
  (map (fn [{:keys [widget/uuid]}]
         (let [widget (p/pull db (widget-report-query) [:widget/uuid uuid])
               budget (p/pull db [:budget/uuid] budget-eid)
               transactions (p/transactions-with-conversions
                              (assoc env :query (transaction-query))
                              (:username auth)
                              {:filter      (:widget/filter widget)
                               :budget-uuid (:budget/uuid budget)})
               report-data (report/generate-data (:widget/report widget) (:widget/filter widget) transactions)]
           (assoc widget :widget/data report-data)))
       widgets))