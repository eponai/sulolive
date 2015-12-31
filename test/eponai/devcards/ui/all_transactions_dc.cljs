(ns eponai.devcards.ui.all-transactions_dc
  (:require-macros [devcards.core :refer [defcard]])
  (:require
    [eponai.client.ui.all_transactions :as a]
    [eponai.devcards.ui.transaction_dc :as t]))

(defn day-props
  ([]
    (day-props [] false))
  ([expanded]
    (day-props [] expanded))
  ([txs expanded]
   (let [day (assoc
               (:transaction/date t/transaction-props)
               :transaction/_date (->> (conj txs nil)
                                       (mapv #(merge t/transaction-props %))))]
     (merge
       day
       {::a/day-expanded? expanded}))))

(defcard
  expanded-day
  (a/->AllTransactions
    {:query/all-dates [(day-props true)]}))

(defcard
  multi-transaction-same-day
  (a/->AllTransactions
    {:query/all-dates [(day-props [{:transaction/amount 200}] true)]}))

(defcard
  month-of-transactions--days-in-reverse-order
  (a/->AllTransactions
    {:query/all-dates [(day-props)
                       (update (day-props) :date/day inc)]}))

(defcard
  month-of-transactions--months-in-reverse-order
  (a/->AllTransactions
    {:query/all-dates [(day-props)
                       (update (day-props) :date/month inc)]}))

(defcard
  month-of-transactions--year-in-reverse-order
  (a/->AllTransactions
    {:query/all-dates [(day-props)
                       (update (day-props) :date/year inc)]}))
