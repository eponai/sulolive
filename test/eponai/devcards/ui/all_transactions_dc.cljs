(ns eponai.devcards.ui.all-transactions_dc
  (:require-macros [devcards.core :refer [defcard]])
  (:require
    [eponai.client.ui.all_transactions :as a]
    [eponai.devcards.ui.transaction_dc :as t]))

(enable-console-print!)
(def day-props (assoc (:transaction/date t/transaction-props)
                 :transaction/_date [t/transaction-props]))

(defcard expanded-day
         (a/->AllTransactions {:query/all-dates [(assoc day-props ::a/day-expanded? true)]}))

(defcard multi-transaction-same-day
         (a/->AllTransactions {:query/all-dates [(update day-props
                                                         :transaction/_date
                                                         conj
                                                         (assoc t/transaction-props
                                                           :transaction/amount 200))]}))
(defcard month-of-transactions--days-in-reverse-order
         (a/->AllTransactions {:query/all-dates [day-props
                                                 (update day-props :date/day inc)]}))

(defcard month-of-transactions--months-in-reverse-order
         (a/->AllTransactions {:query/all-dates [day-props
                                                 (update day-props :date/month inc)]}))

(defcard month-of-transactions--year-in-reverse-order
         (a/->AllTransactions {:query/all-dates [day-props
                                                 (update day-props :date/year inc)]}))
