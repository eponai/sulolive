(ns eponai.common.report-test
  (:require
    [clojure.test.check]
    [clojure.test.check.clojure-test #?(:clj  :refer
                                        :cljs :refer-macros) [defspec]]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as props #?@(:cljs [:include-macros true])]
    [eponai.common.generators :refer [gen-transaction]]
    [eponai.common.report :as report]
    [eponai.common.format :as f]))

(defspec
  test-sum-default
  20
  (props/for-all
    [txs-convs (gen/vector (gen/tuple (gen-transaction) (gen/double* {:min 0.01})))]
    (let [txs (map #(assoc-in (f/transaction (first %))
                              [:transaction/conversion
                               :conversion/rate] (second %)) txs-convs)
          [tx-sum] (report/sum :default txs nil)]
      (zero? (apply - tx-sum (map report/converted-amount
                                  txs))))))

;(defspec
;  test-sum-by-tags
;  20
;  (props/for-all
;    [txs-convs (gen/vector (gen/tuple (gen-transaction) (gen/double* {:min 0.01})))]
;    (let [txs (map #(assoc-in (f/transaction (first %))
;                              [:transaction/conversion
;                               :conversion/rate] (second %)) txs-convs)
;          tx-sums (report/sum :transaction/tags txs nil)
;          sum-by-tag (reduce (fn [m [k v]]
;                               (assoc m k (:value (first v))))
;                             {}
;                             (group-by :name tx-sums))]
;      (every? #(zero? (val %))
;              (reduce (fn [m tx]
;                        (let [tags (:transaction/tags tx)
;                              subtract-by-tag (reduce
;                                                #(let [value (get m (:tag/name %2))
;                                                       calculated (- value (report/converted-amount tx))]
;                                                  (assoc %1 (:tag/name %2) calculated))
;                                                {}
;                                                tags)]
;                          (merge m subtract-by-tag)))
;                      sum-by-tag
;                      txs)))))

(defspec
  test-sum-by-date
  20
  (props/for-all
    [txs-convs (gen/vector (gen/tuple (gen-transaction) (gen/double* {:min 0.01})))]
    (let [txs (map #(assoc-in (f/transaction (first %))
                              [:transaction/conversion
                               :conversion/rate] (second %)) txs-convs)
          tx-sums (report/sum :transaction/date txs nil)
          sum-by-date (reduce (fn [m [k v]]
                               (assoc m k (:value (first v)))) {} (group-by :name tx-sums))]
      (every? #(zero? (val %)) (reduce (fn [m tx]
                                         (let [timestamp (:date/timestamp (:transaction/date tx))]
                                           (update m timestamp - (report/converted-amount tx))))
                                       sum-by-date
                                       txs)))))

(defspec
  test-sum-by-currency
  50
  (props/for-all
    [txs-convs (gen/vector (gen/tuple (gen-transaction) (gen/double* {:min 0.01})))]
    (let [txs (map #(assoc-in (f/transaction (first %))
                              [:transaction/conversion
                               :conversion/rate] (second %)) txs-convs)
          tx-sums (report/sum :transaction/currency txs nil)
          sum-by-cur (reduce (fn [m [k v]]
                               (assoc m k (:value (first v)))) {} (group-by :name tx-sums))]
      (every? #(zero? (val %)) (reduce (fn [m tx]
                                         (let [cur (:currency/code (:transaction/currency tx))]
                                           (update m cur - (report/converted-amount tx))))
                                       sum-by-cur
                                       txs)))))
