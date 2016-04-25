(ns eponai.common.report
  (:require

    #?(:clj [taoensso.timbre :refer [debug]]
       :cljs [taoensso.timbre :refer-macros [debug]])
    #?(:clj [clj-time.coerce :as c]
       :cljs [cljs-time.coerce :as c])
    #?(:clj [clj-time.core :as t]
       :cljs [cljs-time.core :as t])))

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

(defmulti sum (fn [k _ _]
                k))

(defmethod sum :default
  [_ transactions _]
  (let [sum-fn (fn [s tx]
                 (+ s (converted-amount tx)))]
    [(reduce sum-fn 0 transactions)]))

(defmethod sum :transaction/date
  [_ transactions _]
  (let [by-timestamp (group-by #(:date/timestamp (:transaction/date %)) transactions)
        sum-fn (fn [[timestamp ts]]
                 {:name  timestamp
                  :value (reduce (fn [s tx]
                                   (+ s (converted-amount tx)))
                                 0
                                 ts)})
        sum-by-day (mapv sum-fn by-timestamp)]
    (sort-by :name sum-by-day)))

(defmethod sum :transaction/tags
  [_ transactions {:keys [data-filter]}]
  (let [sum-fn (fn [m transaction]
                 (let [
                       include-tag-names (map :tag/name (:filter/include-tags data-filter))
                       exclude-tag-names (map :tag/name (:filter/exclude-tags data-filter))
                       tags (:transaction/tags transaction)
                       filtered-tags (let [filtered-included (if (seq include-tag-names)
                                                               (filter #(contains? (set include-tag-names) (:tag/name %)) tags)
                                                               tags)]
                                       (if (seq exclude-tag-names)
                                         (remove #(contains? (set exclude-tag-names) (:tag/name %)) filtered-included)
                                         filtered-included))]
                   (if (empty? filtered-tags)
                     m
                     (reduce (fn [m2 tagname]
                               (update m2 tagname
                                       (fn [me]
                                         (if me
                                           (+ me (converted-amount transaction))
                                           (converted-amount transaction)))))
                             m
                             (map :tag/name filtered-tags)))))
        sum-by-tag (reduce sum-fn {} transactions)
        ordered (sort-by :value > (reduce #(conj %1 {:name  (first %2) ;tag name
                                                 :value (second %2)}) ;sum for tag
                                      []
                                      sum-by-tag))]
    (if (< 7 (count ordered))
      (conj (vec (take 7 ordered))
            ;; If we have more than 7 tags, it will be too cramped in the chart,
            ;; so group the smallest values into one single data point (one bar)
            (reduce #(let [{c :count :as m} (update %1 :count inc)]
                      (-> m
                          (assoc :name (str c
                                            (if (> c 1)     ; Use plural or singular depending on how many tags are grouped
                                              " tags"
                                              " tag")))
                          (update :value + (:value %2))
                          (update :sub-values conj %2)))
                    {:count 0                               ; Use this count to include in the name.
                     :value 0
                     :sub-values []}
                    (drop 7 ordered)))                      ; Only the rest of the values except the first 7
      ordered)
    ))

(defmethod sum :transaction/currency
  [_ transactions _]
  (let [grouped (group-by #(select-keys (:transaction/currency %) [:currency/code]) transactions)
        sum-fn (fn [[c ts]]
                 (assoc c :currency/sum (reduce (fn [s tx]
                                                (+ s (converted-amount tx)))
                                              0
                                              ts)))
        sum-by-currency (map sum-fn grouped)]
    (reduce #(conj %1
                   {:name  (:currency/code %2)
                    :value (:currency/sum %2)})
            []
            (sort-by :currency/code sum-by-currency))))

(defmulti mean (fn [k _ _ ] k))

(defmethod mean :default
  [_ transactions opts]
  (let [by-timestamp (group-by #(select-keys (:transaction/date %) [:date/timestamp]) transactions)
        [sum-value] (sum :default transactions opts)]
    [(/ sum-value (count by-timestamp))]))

(defmethod mean :transaction/date
  [_ transactions opts]
  (let [by-timestamp (group-by #(select-keys (:transaction/date %) [:date/timestamp]) transactions)
        [sum] (sum :default transactions opts)
        mean-value (/ sum (count by-timestamp))]
    (reduce (fn [l [date _]]
              (if (some? (:date/timestamp date))
                   (conj l {:name  (:date/timestamp date)
                            :value mean-value})
                   l))
            []
            by-timestamp)))

(defmethod mean :transaction/tags
  [_ transactions opts]
  (let [by-timestamp (group-by #(select-keys (:transaction/date %) [:date/timestamp]) transactions)
        sum-by-tag (sum :transaction/tags transactions opts)]
    (map (fn [tag-sum]
           {:name  (:name tag-sum)
            :value (/ (or (:value tag-sum) 0) (count by-timestamp))})
         sum-by-tag)))

(defmulti track (fn [f _ _] (:track.function/id f)))

(defmethod track :track.function.id/sum
  [{:keys [track.function/group-by]} transactions opts]
  {:key    "All transactions sum"
   :id     :track.function.id/sum
   :values (sum group-by transactions opts)})

(defmethod track :track.function.id/mean
  [{:keys [track.function/group-by]} transactions opts]
  {:key    "All Transactions mean"
   :id     :track.function.id/mean
   :values (mean group-by transactions opts)})

(defn goal [g transactions]
  (debug "transactions: " transactions)
  (debug "Generate goal: " g)
  (let [{:keys [goal/value
                goal/cycle]} g
        {:keys [cycle/start]} cycle
        txs-by-timestamp (group-by #(:date/timestamp (:transaction/date %)) transactions)
        today (c/to-long (t/today))
        today-transactions (or (get txs-by-timestamp today) [])]
    (debug "Goal: Transactions: " txs-by-timestamp)
    (debug "Goal today: " today)
    (debug "Goal: Got transactions for today: " today-transactions)

    [{:name  today
      :value (reduce (fn [s tx]
                       (+ s (converted-amount tx)))
                     0
                     today-transactions)
      :max   value}]
    ;(let [sum-by-day (sum :transaction/date {} transactions)]
    ;  (debug "Sum for goal: " sum-by-day)
    ;  (map (fn [data-point]
    ;         (debug "Using data-point " data-point)
    ;         (assoc data-point :max value))
    ;       sum-by-day))
    )
  )

(defn generate-data [report transactions & [opts]]
  ;(debug "Generate data goal: " report)
  ;(debug "generate for Transactions: " transactions)
  (cond
    (some? (:report/track report))
    (let [functions (get-in report [:report/track :track/functions])
          track-fn (fn [f]
                     (debug "Make calc: " f " with opts: " opts)
                     (track f transactions opts))]
      (debug "Generated data: " (mapv track-fn functions))
      (map track-fn functions))

    (some? (:report/goal report))
    (let [g (:report/goal report)
          ret (goal g transactions)]
      (debug "Generated goal data: " ret)
      ret)))
