(ns eponai.client.report)

(defmulti sum (fn [k _] k))

(defmethod sum :default
  [_ transactions attr]
  (let [sum-fn (fn [s tx]
                 (let [add-number (if (number? (get tx attr))
                                    (get tx attr)
                                    1)]
                   (+ s add-number)))]
    {:key    "All Transactions"
     :values [0 (reduce sum-fn 0 transactions)]}))

(defmethod sum :transaction/date
  [_ transactions attr]
  (let [grouped (group-by :transaction/date transactions)
        sum-fn (fn [[day ts]]
                 (assoc day :date/sum (reduce (fn [s tx]
                                                (let [add-number (if (number? (get tx attr))
                                                                   (get tx attr)
                                                                   1)]
                                                  (+ s add-number)))
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
                                       (let [add-number (if (number? (get transaction attr))
                                                          (get transaction attr)
                                                          1)]
                                         (if me
                                           (+ me add-number)
                                           add-number)))))
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
  [{:keys [report/group-by report/function]} _ transactions]
  (let [attribute (:report.function/attribute function)]
    (sum group-by transactions (or attribute :transaction/amount))))

(defn create [report transactions]
  (let [k (get-in report [:report/function :report.function/id])]
    (calculation report (or k :report.function.id/sum) transactions)))
