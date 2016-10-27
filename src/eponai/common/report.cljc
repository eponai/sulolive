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

(defn converted-amount [{:keys [transaction/conversion
                                transaction/amount]}]
  (let [rate (:conversion/rate conversion)]
    (if (and conversion
             (number? rate)
             (not #?(:clj  (Double/isNaN rate)
                     :cljs (js/isNaN rate))))
      #?(:clj  (let [ret (with-precision 10 (bigdec (/ amount
                                                       rate)))]
                 ;(prn "Conversion: " ret " amount " amount "rate " rate)
                 ret)
         :cljs (/ amount
                  rate))
      0)))

(defn time-range [start & [end]]
  (let [month (c/from-long start)
        end-date (if end (c/from-long end) (t/earliest (date/today) (date/last-day-of-month (t/month month))))]
    (map date/date->long (p/periodic-seq month (t/plus end-date (t/days 1)) (t/days 1)))))

(defn avg-by-day [value]
  (let [days (count (time-range (date/month->long (date/today))))]
    (when (pos? days) (/ value days))))

(defn left-by-end-of-month [limit avg-spent]
  (let [days-left (count (time-range (date/month->long (date/tomorrow)) (date/date->long (date/last-day-of-this-month))))]
    (- limit (* (t/number-of-days-in-the-month (date/today)) avg-spent))))

(defn summary-info [limit spent]
  (let [avg (avg-by-day spent)
        ]
    {:avg-spent avg
     :left-by-end (left-by-end-of-month limit avg)}))

(defn summary
  "Calculate how much has been spent on housing and transport, as well as what the current balance is relative to the user's income."
  [transactions]
  (let [
        ; Group transactions by month to make calculations on monthly basis.
        txs-by-month (group-by #(date/month->long (:transaction/date %)) transactions)
        ; Reduce function to calculate how much is spent on housing and transport, and how much money user has left.
        summary (fn [res tx]
                  (let [conv-amount (converted-amount tx)]
                    (cond
                      (= (get-in tx [:transaction/type :db/ident]) :transaction.type/income)
                      (update res :limit + conv-amount)

                      (= "Housing" (:transaction/category tx))
                      (-> res
                          (update :housing + conv-amount)
                          (update :spent - conv-amount))

                      (= "Transport" (:transaction/category tx))
                      (-> res
                          (update :transport + conv-amount)
                          (update :spent + conv-amount))
                      :else
                      (update res :spent + conv-amount))))

        res-by-month (reduce
                       (fn [res [mo txs]]
                         (let [{:keys [limit spent] :as month-res}
                               (reduce summary
                                       {:housing 0 :transport 0 :spent 0 :limit 0}
                                       txs)]
                           (assoc res mo (merge month-res
                                                (summary-info limit spent)))))
                       {}
                       txs-by-month)
        values (second (first res-by-month))]

    values))

(defn value-range [values]
  (let [[low high] (cond (empty? values)
                         [0 10]
                         (= 1 (count values))
                         (let [value (first values)
                               max-val (max (:balance value) (:spent value))]
                           (if (neg? max-val)
                             [max-val 0]
                             [0 max-val]))

                         (< 1 (count values))
                         [(apply min (map #(min (:balance %) (:spent %)) values))
                          (apply max (map #(max (:balance %) (:spent %)) values))])]
    (if (= low high)
      [low (+ high 10)]
      [low  high])))

(defn balance-vs-spent
  "Calculate what expenses per day by month, and how the balance has evolved in relation to those expenses.

  Balance will be calculated based on income transactions."
  [transactions]
  (let [
        ; Group transactions by month to make calculations on a monhtly basis.
        txs-by-month (group-by #(date/month->long (:transaction/date %)) transactions)

        ; Reduce function for sum of expenses and incomes.
        sum-daily-transactions (fn [res tx]
                                 (if (= (get-in tx [:transaction/type :db/ident]) :transaction.type/expense)
                                   (update res :expenses + (converted-amount tx))
                                   (update res :income + (converted-amount tx))))

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

        ;res-by-month (map
        ;               ; Spent vs balance by month, given a month timestamp and a list of transactions for that month.
        ;               (fn [[mo txs]]
        ;                 (let [time-span (time-range mo)    ; Create a timespan for the entire month, 1st to last day of month.
        ;                       by-timestamp (group-by #(:date/timestamp (:transaction/date %)) txs)] ; Group transactions by date within month for easy access later
        ;
        ;                   ; Calculate spent vs balance for each month and assoc to a month timestamp in the resulting map.
        ;                   (reduce spent-balance
        ;                           {:balance      0
        ;                            :by-timestamp by-timestamp
        ;                            :values       {}}
        ;                           time-span)))
        ;               txs-by-month)

        this-month-timestamp (date/month->long (date/today))
        txs-this-month (get txs-by-month (date/month->long (date/first-day-of-this-month)))
        report-this-month (let [time-span (time-range this-month-timestamp)
                                by-timestamp (group-by #(:date/timestamp (:transaction/date %)) txs-this-month)]
                                  ; Calculate spent vs balance for each month and assoc to a month timestamp in the resulting map.
                                  (reduce spent-balance
                                          {:balance      0
                                           :by-timestamp by-timestamp
                                           :values       {}}
                                          time-span))

        values (sort-by :date
                        (map
                          (fn [[k v]]
                            (assoc v :date k))
                          (:values report-this-month)))]

    (debug "report this month: " (sort-by :date
                                          (map
                                            (fn [[k v]]
                                              (assoc v :date k))
                                            (:values report-this-month))))
    {:data-points values
     :x-domain    [this-month-timestamp (date/date->long (date/last-day-of-this-month))]
     :y-domain    (value-range values)}))


