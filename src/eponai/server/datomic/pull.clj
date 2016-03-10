(ns eponai.server.datomic.pull
  (:require
    [clojure.walk :refer [walk]]
    [datomic.api :as d]
    [eponai.common.database.pull :as p]
    [eponai.common.parser.util :as parser]
    [eponai.common.report :as report]))

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

(defn txs-with-conversions [db query {:keys [tx-ids user/uuid]}]
  (let [tx-conv-tuples (p/all-with db {:find-pattern '[?t ?e ?e2]
                                       :symbols      {'[?t ...] tx-ids
                                                      '?uuid uuid}
                                       :where        '[[?t :transaction/date ?d]
                                                       [?e :conversion/date ?d]
                                                       [?t :transaction/currency ?cur]
                                                       [?e :conversion/currency ?cur]
                                                       [?u :user/uuid ?uuid]
                                                       [?u :user/currency ?u-cur]
                                                       [?e2 :conversion/currency ?u-cur]
                                                       [?e2 :conversion/date ?d]]})
        tx-convs-by-tx-id (group-by first tx-conv-tuples)
        transactions (map (fn [tx]
                            (let [transaction (p/pull db query tx)
                                  [[_ tx-conv user-conv]] (get tx-convs-by-tx-id tx)]
                              (if tx-conv
                                (let [;; All rates are relative USD so we need to pull what rates the user currency has,
                                      ;; so we can convert the rate appropriately for the user's selected currency
                                      user-currency-conversion (p/pull db '[:conversion/rate] user-conv)
                                      transaction-conversion (p/pull db '[:conversion/rate] tx-conv)]
                                  (assoc
                                    transaction
                                    :transaction/conversion
                                    ;; Convert the rate from USD to whatever currency the user has set
                                    ;; (e.g. user is using SEK, so the new rate will be
                                    ;; conversion-of-transaction-currency / conversion-of-user-currency
                                    {:conversion/rate (with-precision 10
                                                        (bigdec (/ (:conversion/rate transaction-conversion)
                                                                   (:conversion/rate user-currency-conversion))))}))
                                transaction)))
                          tx-ids)]
    transactions))

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
     {:transaction/tags [:tag/name]}
     {:transaction/date [:date/ymd
                         :date/timestamp]}]))

(defn widgets-with-data [{:keys [db parser] :as env} widgets]
  (map (fn [{:keys [widget/uuid]}]
         (let [widget (p/pull db (widget-report-query) [:widget/uuid uuid])
               {:keys [query/transactions]} (parser env [`({:query/transactions ~(transaction-query)}
                                                            {:filter ~(:widget/filter widget)})])
               report-data (report/generate-data (:widget/report widget) (:widget/filter widget) transactions)]
           (assoc widget :widget/data report-data)))
       widgets))