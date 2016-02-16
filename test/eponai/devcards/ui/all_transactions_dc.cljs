(ns eponai.devcards.ui.all-transactions_dc
  (:require-macros [devcards.core :refer [defcard]])
  (:require
    [eponai.client.ui.all_transactions :as a]))

(defn transaction-props* [name [y m d] amount currency tags]
  {:transaction/title    name
   :transaction/amount   amount
   :transaction/currency {:currency/code currency}
   :transaction/tags     (mapv #(hash-map :tag/name %) tags)
   :transaction/date     {:date/ymd   (str y "-" m "-" d)
                          :date/year  y
                          :date/month m
                          :date/day   d}})

(def transaction-props
  (transaction-props* "Coffee" [2015 10 16] 140 "THB"
                      ["thailand" "2015" "chiang mai"]))

(defn day-props
  ([]
    (day-props [] false))
  ([expanded]
    (day-props [] expanded))
  ([txs expanded]
   (let [day (assoc
               (:transaction/date transaction-props)
               :transaction/_date (->> (conj txs nil)
                                       (mapv #(merge transaction-props %))))]
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
