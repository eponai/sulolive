(ns flipmunks.budget.ui.transactions_dc
  (:require [devcards.core :as dc]
            [flipmunks.budget.ui.transactions :as t])
  (:require-macros [devcards.core :refer [defcard]])) 

(defn transaction-props [name [y m d] amount currency tags]
  {:transaction/name name
   :transaction/date {:date/ymd (str y "-" m "-" d)
                      :date/year y :date/month m :date/day d}
   :transaction/amount amount
   :transaction/currency {:currency/name currency}
   :transaction/tags (mapv #(hash-map :tag/name %) tags)})

(def test-transaction 
  (transaction-props "coffee" [2015 10 16] 140 "THB" 
                     ["thailand" "2015" "chiang mai"]))

(defcard transaction-card
  (t/transaction test-transaction))

(defcard transaction-with-details
  (t/transaction (assoc test-transaction
                        :transaction/details
                        "Great latte next to the dumpling place. We should come back here next time we're in Chaing Mai, Thailand.")))

(defcard transaction-with-tags
  (t/transaction (assoc test-transaction
                        :ui.transaction/show-tags
                        true)))

(defcard transaction-with-details-and-tags
  (t/transaction (assoc test-transaction
                        :transaction/details
                        "Very good latte! We should come back."
                        :ui.transaction/show-tags
                        true)))

(defcard day-of-transactions
  (t/day-of-transactions (assoc (:transaction/date test-transaction)
                                :transaction/_date [test-transaction])))
