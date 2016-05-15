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

(defmulti sum (fn [k _ _]
                k))

(defmethod sum :default
  [_ transactions _]
  (let [sum-fn (fn [s tx]
                 (+ s (converted-amount tx)))]
    [(reduce sum-fn 0 transactions)]))

;; Data helper
(defn zero-padding-to-time-series-data [values & [opts]]
  (debug "Create report with opts: " opts)
  (let [by-timestamp (group-by :name values)
        start (or (date/date-time (:start opts))
                  (date/date-time (apply min (keys by-timestamp))))
        end (or (date/date-time (:end opts))
                (date/date-time (t/plus (date/today) (t/days 1))))] ;;Use tomorrow as end cause we want to include today.
    ;_ (debug "Create report with start/end: " start end)
    ;_ (debug "Create report period step: " (or (:step opts) (t/days 1)))
    ;_ (debug "Create report got time-period: " (p/periodic-seq start end (or (:step opts) (t/days 1))))
    (if (and start end)
      (let [time-range (p/periodic-seq start end (or (:step opts) (t/days 1)))]
        (map (fn [date]
               (let [t (date/date->long date)
                     [add-value] (get by-timestamp t)]
                 (or add-value {:name t :value 0})))
             time-range))
      [])))

(defmethod sum :transaction/date
  [_ transactions _]
  (let [by-timestamp (group-by #(:date/timestamp (:transaction/date %)) transactions)
        sum-fn (fn [[timestamp ts]]
                 {:name  timestamp
                  :value (reduce (fn [s tx]
                                   (+ s (converted-amount tx)))
                                 0
                                 ts)})]
    (mapv sum-fn by-timestamp)))

(defmethod sum :transaction/tags
  [_ transactions {:keys [data-filter]}]
  (let [sum-fn (fn [m transaction]
                 (let [include-tag-names (map :tag/name (:filter/include-tags data-filter))
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
    ;; If we have more than 7 tags, it will be too cramped in the chart,
    ;; so group the smallest values into one single data point (one bar)
    (if (< 7 (count ordered))
      (conj (vec (take 7 ordered))
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
      ordered)))

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
  [{:keys [track.function/group-by]} transactions {:keys [data-filter] :as opts}]
  (let [values (sum group-by transactions opts)]
    {:key    "All transactions sum"
     :id     :track.function.id/sum
     :values (if (= group-by :transaction/date)
               (zero-padding-to-time-series-data values)
               values)}))

(defmethod track :track.function.id/mean
  [{:keys [track.function/group-by]} transactions opts]
  {:key    "All Transactions mean"
   :id     :track.function.id/mean
   :values (mean group-by transactions opts)})

(defmethod track :report.function.id/tags
  [{:keys [track.function/group-by]} transactions opts]
  (let [all-tags (map :transaction/tags transactions)
        tag-relations (reduce (fn [m1 tags]
                                (reduce (fn [m2 tag]
                                          (update m2 (:tag/name tag) #(if (some? %)
                                                                       (concat % (map :tag/name tags))
                                                                       (map :tag/name tags))))
                                        m1
                                        tags))
                              {}
                              all-tags)]
    (debug "Tag-data: " (map (fn [me] {:name (key me) :children (vec (remove #(= (key me) %) (val me)))}) tag-relations))
    {:key    "All Transactions mean"
     :id     :track.function.id/mean
     :values (map (fn [me] {:name (key me) :children (vec (remove #(= (key me) %) (val me)))}) tag-relations)}))

(defmulti goal (fn [g _ ] (get-in g [:goal/cycle :cycle/period])))

(defmethod goal :cycle.period/day
  [g transactions]
  (let [{:keys [goal/value
                goal/cycle]} g
        {:keys [cycle/start
                cycle/period
                cycle/period-count]} cycle
        today-long (date/date->long (date/today))
        txs-by-timestamp (group-by #(:date/timestamp (:transaction/date %)) transactions)
        today-transactions (or (get txs-by-timestamp today-long) [])]
    [{:date   today-long
      :limit  value
      :values [{:name  today-long
                :value (reduce (fn [s tx]
                                 (+ s (converted-amount tx)))
                               0
                               today-transactions)}]}]))

(defmethod goal :cycle.period/month
  [g transactions]
  (let [{:keys [goal/value
                goal/cycle]} g
        {:keys [cycle/start
                cycle/period
                cycle/period-count]} cycle]
    (let [by-month (group-by #(let [date (:transaction/date %)]
                               (date/month->long date)) transactions)
          sum-by-month (map (fn [[k v]]
                              (let [month (c/from-long k)
                                    end (min (date/date->long (date/today)) (date/date->long (t/last-day-of-the-month month)))
                                    values (zero-padding-to-time-series-data (sum :transaction/date v {}) {:start month
                                                                                                           :end   (t/plus (c/from-long end) (t/days 1))})
                                    #?@(:clj  [avg (with-precision 10 (/ value (t/number-of-days-in-the-month month)))]
                                        :cljs [avg (/ value (t/number-of-days-in-the-month month))])
                                    last-day (date/date->long (t/last-day-of-the-month month))]
                                {:date   k
                                 :limit  value
                                 :values (loop [input values
                                                output []
                                                tot 0]
                                           (if-let [v (:value (first input))]
                                             (recur (rest input)
                                                    (conj output (assoc (first input) :value (+ tot v)
                                                                                      :key "user-input"))
                                                    (+ tot v))
                                             output))
                                 :guide  (loop [start k
                                                output []
                                                tot 0]
                                           (if (<= start last-day)
                                             (recur (date/date->long (t/plus (date/date-time start) (t/days 1)))
                                                    (conj output {:value (+ tot avg)
                                                                  :name start
                                                                  :key   "guideline"})
                                                    (+ tot avg))
                                             output))}))
                               by-month)]
      (sort-by :date sum-by-month))))

(defn generate-data [report ts & [opts]]
  ;(debug "Generate data goal: " report)
  (let [transactions (filter #(let [t (:transaction/type %)]
                               ;; Is it a ref or inlined ref?
                               (= (or (:db/ident t) t)
                                  :transaction.type/expense)) ts)] ;;TODO: do this some better place?
    (cond
      (some? (:report/track report))
      (let [functions (get-in report [:report/track :track/functions])
            track-fn (fn [f]
                       ;(debug "Make calc: " f " with opts: " opts)
                       (track f transactions opts))]
        ;(debug "Generated data: " (mapv track-fn functions))
        (map track-fn functions))

      (some? (:report/goal report))
      (let [g (:report/goal report)
            ret (goal g transactions)]
        ;(debug "Generated goal data: " ret)
        ret))))
