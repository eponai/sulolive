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

(defmulti sum (fn [k _ _] k))

(defmethod sum :default
  [_ _ transactions]
  (let [sum-fn (fn [s tx]
                 (+ s (converted-amount tx)))]
    {:key    "All Transactions"
     :values [(reduce sum-fn 0 transactions)]}))

(defmethod sum :transaction/date
  [_ _ transactions]
  (let [grouped (group-by #(select-keys (:transaction/date %) [:date/timestamp]) transactions)
        sum-fn (fn [[day ts]]
                 (assoc day :date/sum (reduce (fn [s tx]
                                                (+ s (converted-amount tx)))
                                              0
                                              ts)))
        sum-by-day (map sum-fn grouped)]
    [{:key    "All Transactions"
      :values (reduce (fn [l date]
                        (if (some? (:date/timestamp date))
                          (conj l
                                {:name  (:date/timestamp date) ;date timestamp
                                 :value (:date/sum date)})
                          l)) ;sum for date
                      []
                      (sort-by :date/timestamp sum-by-day))}]))

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

    [{:key    "All Transactions"
      :values (sort-by :name (reduce #(conj %1 {:name  (first %2) ;tag name
                                                :value (second %2)}) ;sum for tag
                                     []
                                     sum-by-tag))}]))

(defmethod sum :transaction/currency
  [_ data-filter transactions]
  (let [grouped (group-by #(select-keys (:transaction/currency %) [:currency/code]) transactions)
        sum-fn (fn [[c ts]]
                 (assoc c :currency/sum (reduce (fn [s tx]
                                                (+ s (converted-amount tx)))
                                              0
                                              ts)))
        sum-by-currency (map sum-fn grouped)]
    [{:key    "All Transactions"
      :values (reduce #(conj %1
                             {:name  (:currency/code %2)
                              :value (:currency/sum %2)})
                      []
                      (sort-by :currency/code sum-by-currency))}]))


(defmulti calculation (fn [_ function-id _ _] function-id))

(defmethod calculation :report.function.id/sum
  [{:keys [report/group-by]} _ data-filter transactions]
  (sum group-by data-filter transactions))

(defn generate-data [report data-filter transactions]
  (let [functions (:report/functions report)
        k (:report.function/id (first functions))]
    (debug "Make calc: " k " with filter: " data-filter)
    (calculation report (or k :report.function.id/sum) data-filter transactions)))
