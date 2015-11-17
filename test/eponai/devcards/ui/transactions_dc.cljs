(ns eponai.devcards.ui.transactions_dc
  (:require [devcards.core :as dc]
            [sablono.core :refer-macros [html]]
            [eponai.client.ui.transactions :as t])
  (:require-macros [devcards.core :refer [defcard]]))

(defn transaction-props* [name [y m d] amount currency tags]
  {:transaction/name     name
   :transaction/amount   amount
   :transaction/currency {:currency/name currency}
   :transaction/tags     (mapv #(hash-map :tag/name %) tags)
   :transaction/date     {:date/ymd  (str y "-" m "-" d)
                          :date/year y :date/month m :date/day d}})

(def transaction-props
  (transaction-props* "Coffee" [2015 10 16] 140 "THB"
                      ["thailand" "2015" "chiang mai"]))

(defcard transaction-card
         (t/->Transaction transaction-props))

(defcard transaction-with-details
         (t/->Transaction (assoc transaction-props
                          :transaction/details
                          "Great latte next to the dumpling place. We should come back here next time we're in Chaing Mai, Thailand.")))

(defcard transaction-with-tags
         (t/->Transaction (assoc transaction-props
                          :ui.transaction/show-tags
                          true)))

(def t-with-details-and-tags
  (assoc transaction-props
    :transaction/details
    "Very good latte! We should come back."
    :ui.transaction/show-tags
    true))

(defcard transaction-with-details-and-tags
         (t/->Transaction t-with-details-and-tags))

(def day-props (assoc (:transaction/date transaction-props)
                 :transaction/_date [transaction-props]))

(defcard day-of-transactions
         (t/->DayTransactions day-props))

(defcard day-of-transactions-expanded
         (t/->DayTransactions (assoc day-props
                                  :ui.day/expanded
                                  true)))

(def expanded-trans (-> day-props
                        (assoc :ui.day/expanded true)
                        (update :transaction/_date conj t-with-details-and-tags)))

(defcard day-of-transactions-expanded--2-transactions
         (t/->DayTransactions expanded-trans))

(defcard two-days-of-transactions
         (html [:div
                (t/->DayTransactions expanded-trans)
                (t/->DayTransactions day-props)]))

(defcard month-of-transactions--days-in-reverse-order
         (t/->AllTransactions {:query/all-dates [day-props
                                                (update day-props :date/day inc)]}))

(defcard month-of-transactions--months-in-reverse-order
         (t/->AllTransactions {:query/all-dates [day-props
                                                (update day-props :date/month inc)]}))

(defcard month-of-transactions--year-in-reverse-order
         (t/->AllTransactions {:query/all-dates [day-props
                                                (update day-props :date/year inc)]}))
