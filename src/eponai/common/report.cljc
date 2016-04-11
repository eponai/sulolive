(ns eponai.common.report
  (:require
    #?(:clj [taoensso.timbre :refer [debug]]
       :cljs [taoensso.timbre :refer-macros [debug]])))

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

(defmulti sum (fn [k _ ts]
                k))

(defmethod sum :default
  [k _ transactions]
  (debug "Generating sum data: " k " " transactions)
  (let [sum-fn (fn [s tx]
                 (+ s (converted-amount tx)))]
    [(reduce sum-fn 0 transactions)]))

(defmethod sum :transaction/date
  [_ _ transactions]
  (let [grouped (group-by #(select-keys (:transaction/date %) [:date/timestamp]) transactions)
        sum-fn (fn [[day ts]]
                 (assoc day :date/sum (reduce (fn [s tx]
                                                (+ s (converted-amount tx)))
                                              0
                                              ts)))
        sum-by-day (map sum-fn grouped)]
    (reduce (fn [l date]
              (if (some? (:date/timestamp date))
                (conj l
                      {:name  (:date/timestamp date) ;date timestamp
                       :value (:date/sum date)})
                l))                             ;sum for date
            []
            (sort-by :date/timestamp sum-by-day))))

(defmethod sum :transaction/tags
  [_ data-filter transactions]
  (let [sum-fn (fn [m transaction]
                 (let [include-tag-names (map :tag/name (:filter/include-tags data-filter))
                       tags (:transaction/tags transaction)
                       filtered-tags (if (seq include-tag-names)
                                       (filter #(contains? (set include-tag-names) (:tag/name %)) tags)
                                       tags)]
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
        sum-by-tag (reduce sum-fn {} transactions)]

    (sort-by :name (reduce #(conj %1 {:name  (first %2) ;tag name
                                      :value (second %2)}) ;sum for tag
                           []
                           sum-by-tag))))

(defmethod sum :transaction/currency
  [_ _ transactions]
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
  [_ data-filter transactions]
  (let [by-timestamp (group-by #(select-keys (:transaction/date %) [:date/timestamp]) transactions)
        [sum-value] (sum :default data-filter transactions)]
    [(/ sum-value (count by-timestamp))]))

(defmethod mean :transaction/date
  [_ data-filter transactions]
  (let [by-timestamp (group-by #(select-keys (:transaction/date %) [:date/timestamp]) transactions)
        [sum] (sum :default data-filter transactions)
        mean-value (/ sum (count by-timestamp))]
    (debug "Found mean value: " mean-value)
    (reduce (fn [l [date _]]
              (if (some? (:date/timestamp date))
                   (conj l {:name  (:date/timestamp date)
                            :value mean-value})
                   l))
            []
            by-timestamp)))

(defmethod mean :transaction/tags
  [_ data-filter transactions]
  (let [by-timestamp (group-by #(select-keys (:transaction/date %) [:date/timestamp]) transactions)
        sum-by-tag (sum :transaction/tags data-filter transactions)]
    (map (fn [tag-sum]
           {:name  (:name tag-sum)
            :value (/ (or (:value tag-sum) 0) (count by-timestamp))})
         sum-by-tag)))

(defmulti track (fn [f _ _] (:track.function/id f)))

(defmethod track :track.function.id/sum
  [{:keys [track.function/group-by]} data-filter transactions]
  {:key    "All transactions sum"
   :id     :track.function.id/sum
   :values (sum group-by data-filter transactions)})

(defmethod track :track.function.id/mean
  [{:keys [track.function/group-by]} data-filter transactions]
  {:key    "All Transactions mean"
   :id     :track.function.id/mean
   :values (mean group-by data-filter transactions)})

(defn generate-data [report data-filter transactions]
  (debug "Generate data: " report)
  (debug "generate for Transactions: " transactions)
  (let [functions (get-in report [:report/track :track/functions])
        track-fn (fn [f]
                         (debug "Make calc: " f " with filter: " data-filter)
                         (track f data-filter transactions))]
    (debug "Generated data: " (mapv track-fn functions))
    (map track-fn functions)))
