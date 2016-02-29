(ns eponai.common.report)

(defn converted-amount [tx]
  #?(:clj  (with-precision 2 (/ (:transaction/amount tx)
                                (:conversion/rate (:transaction/conversion tx))))
     :cljs (/ (:transaction/amount tx)
              (:conversion/rate (:transaction/conversion tx)))))

(defmulti sum (fn [k _ _] k))

(defmethod sum :default
  [_ transactions _]
  (let [sum-fn (fn [s tx]
                 (let [add-number (if (number? (get tx :transaction/amount))
                                    (get tx :transaction/amount)
                                    1)]
                   (+ s add-number)))]
    {:key    "All Transactions"
     :values [(reduce sum-fn 0 transactions)]}))

(defmethod sum :transaction/date
  [_ transactions _]
  (let [grouped (group-by :transaction/date transactions)
        sum-fn (fn [[day ts]]
                 (assoc day :date/sum (reduce (fn [s tx]
                                                (+ s (converted-amount tx)))
                                              0
                                              ts)))
        sum-by-day (map sum-fn grouped)]
    [{:key    "All Transactions"
      :values (reduce #(conj %1
                             {:name  (:date/timestamp %2)   ;date timestamp
                              :value (:date/sum %2)})       ;sum for date
                      []
                      (sort-by :date/timestamp sum-by-day))}]))

(defmethod sum :transaction/tags
  [_ transactions attr]
  (let [sum-fn (fn [m transaction]
                 (let [tags (:transaction/tags transaction)]
                   (reduce (fn [m2 tagname]
                             (update m2 tagname
                                     (fn [me]
                                       (if me
                                         (+ me (converted-amount transaction))
                                         (converted-amount transaction)))))
                           m
                           (if (empty? tags)
                             ["no tags"]
                             (map :tag/name tags)))))
        sum-by-tag (reduce sum-fn {} transactions)]

    [{:key    "All Transactions"
      :values (reduce #(conj %1 {:name  (first %2)          ;tag name
                                 :value (second %2)})       ;sum for tag
                      []
                      sum-by-tag)}]))


(defmulti calculation (fn [_ function-id _] function-id))

(defmethod calculation :report.function.id/sum
  [{:keys [report/group-by report/functions]} _ transactions]
  (let [attribute (:report.function/attribute (first functions))]
    (sum group-by transactions :transaction/amount)))

(defn create [report transactions]
  (let [functions (:report/functions report)
        k (:report.function/id (first functions))]
    (calculation report (or k :report.function.id/sum) transactions)))