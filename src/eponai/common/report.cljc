(ns eponai.common.report
  (:require
    [eponai.common.format.date :as date]
    #?(:clj [taoensso.timbre :refer [debug]]
       :cljs [taoensso.timbre :refer-macros [debug]])
    #?(:clj
            [clj-time.coerce :as c]
       :cljs [cljs-time.coerce :as c])
    #?(:clj
            [clj-time.core :as t]
       :cljs [cljs-time.core :as t])
    #?(:clj
            [clj-time.periodic :as p]
       :cljs [cljs-time.periodic :as p])))

(defn nan? [x]
  #?(:clj  (Double/isNaN x)
     :cljs (js/isNaN x)))

(defn amount-with-fees [{:keys [transaction/amount transaction/conversion transaction/fees]}]
  (when-let [rate (:conversion/rate conversion)]
    (when (and (and conversion (number? rate) (not (nan? rate))))
      (let [fee-amount (transduce (map (fn [fee]
                                         (condp = (:transaction.fee/type fee)
                                           :transaction.fee.type/absolute
                                           (/ (:transaction.fee/value fee)
                                              (get-in fee [:transaction.fee/conversion :conversion/rate] 1))
                                           :transaction.fee.type/relative
                                           (* amount 0.01 (:transaction.fee/value fee)))))
                                  + 0 fees)]
        (+ fee-amount #?(:clj  (with-precision 10 (bigdec (/ amount rate)))
                         :cljs (/ amount rate)))))))

(defn converted-amount [tx]
  (or (amount-with-fees tx) 0))

(defn date-range [start-date end-date]
  (p/periodic-seq start-date (t/plus end-date (t/days 1)) (t/days 1)))

(defn summary
  "Calculate how much has been spent on housing and transport, as well as what the current balance is relative to the user's income."
  [transactions start-date end-date]
  (let [
        ; Reduce function to calculate how much is spent on housing and transport, and how much money user has left.
        summary (fn [res tx]
                  (let [conv-amount (converted-amount tx)
                        transaction-type (or (get-in tx [:transaction/type :db/ident]) (:transaction/type tx))
                        category-name (get-in tx [:transaction/category :category/name])]
                    (cond
                      (= transaction-type :transaction.type/income)
                      (update res :limit + conv-amount)

                      (= "Housing" category-name)
                      (-> res
                          (update :housing + conv-amount)
                          (update :spent + conv-amount)
                          )

                      (= "Transport" category-name)
                      (-> res
                          (update :transport + conv-amount)
                          (update :spent + conv-amount)
                          )
                      :else
                      (-> res
                          (update :spent + conv-amount)
                          (update :daily-spent + conv-amount)))))
        {:keys [housing transport spent limit daily-spent] :as report-summary}
        (reduce summary
                {:housing 0 :transport 0 :spent 0 :limit 0 :daily-spent 0}
                transactions)
        days-in-period (count (date-range start-date end-date))
        days-left (count (date-range (date/today) end-date))
        avg-by-day (when (pos? days-in-period)
                     (/ daily-spent days-in-period))]
    (assoc report-summary
      :budget (- limit housing transport)
      :left-by-end (- limit spent (* avg-by-day days-left))
      :avg-daily-spent avg-by-day)))

(defn value-range [values]
  (let [[low high] (cond (empty? values)
                         [0 10]
                         (= 1 (count values))
                         (let [value (first values)
                               max-val (max (:balance value) (:spent value))
                               min-val (min (:balance value) (:spent value) 0)]
                           [min-val max-val])

                         (< 1 (count values))
                         [(apply min (map #(min (:balance %) (:spent %)) values))
                          (apply max (map #(max (:balance %) (:spent %)) values))])]
    (if (= low high)
      [low (+ high 10)]
      [low high])))

(defn transaction-type= [t] (comp #{t} :db/ident :transaction/type))
(def income? (transaction-type= :transaction.type/income))
(def expense? (transaction-type= :transaction.type/expense))
(def housing-or-transport? (comp some? #{"Housing" "Transport"} :category/name :transaction/category))

(defn sum-large-expenses [transactions]
  (transduce (comp (filter expense?)
                   (filter housing-or-transport?)
                   (map converted-amount))
             + 0 transactions))

(defn adjusted-income
  "Assoc's ::adjusted-amount to incomes, where large expenses
   for that 'period' has been removed."
  [txs]
  {:pre [(every? #(or (income? %) (expense? %)) txs)]}
  (lazy-seq
    (when (seq txs)
      (let [[expenses txs2] (split-with expense? txs)
            [incomes txs3] (split-with income? txs2)
            sum-to-adjust (/ (sum-large-expenses expenses) (count incomes))]
        (concat expenses
                (map #(assoc % ::adjusted-amount (- (converted-amount %) sum-to-adjust)) incomes)
                (adjusted-income txs3))))))

(defn balance-vs-spent
  "Calculate what expenses per day by month, and how the balance has evolved in relation to those expenses.

  Balance will be calculated based on income transactions."
  [transactions start-date end-date]
  (let [; Reduce function for sum of expenses and incomes.
        sum-daily-transactions (fn [res tx]
                                 (if (= (get-in tx [:transaction/type :db/ident]) :transaction.type/expense)
                                   (update res :expenses + (converted-amount tx))
                                   (update res :income + (::adjusted-amount tx))))

        ; Reduce function to calculate spent amount per day, and balance for each day.
        spent-balance (fn [{:keys [balance by-timestamp] :as res} timestamp]
                        (let [; Transactions for this day
                              txs (get by-timestamp timestamp)

                              ; Sum transaction incomes and expenses for this particular day (timestamp)
                              {:keys [expenses income]} (reduce sum-daily-transactions
                                                                {:expenses 0
                                                                 :income   0}
                                                                txs)

                              ; New balance for this day given the old balance and incomes and expenses for this day
                              new-balance (+ balance (- income expenses))]
                          (-> res
                              (assoc :balance new-balance)  ; Update current balance, to use as start balance in future days.
                              (assoc-in [:values timestamp] {:spent   expenses
                                                             :balance new-balance})))) ; Set the balance for this day, and how much was spent.

        report-this-month (let [daily-txs-by-timestamp (->> transactions
                                                           (adjusted-income)
                                                            (remove housing-or-transport?)
                                                            (group-by #(:date/timestamp (:transaction/date %))))]
                                  ; Calculate spent vs balance for each month and assoc to a month timestamp in the resulting map.
                                  (reduce spent-balance
                                          {:balance      0
                                           :by-timestamp daily-txs-by-timestamp
                                           :values       {}}
                                          (map date/date->long (date-range start-date end-date))))

        values (sort-by :date
                        (map
                          (fn [[k v]]
                            (assoc v :date k))
                          (:values report-this-month)))]
    {:data-points values
     :x-domain    [(date/date->long start-date) (date/date->long end-date)]
     :y-domain    (value-range values)}))


