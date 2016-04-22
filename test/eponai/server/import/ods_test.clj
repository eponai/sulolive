(ns eponai.server.import.ods-test
  (:require [clojure.test :refer :all]
            [eponai.server.import.ods :as e]))

(comment
  "(doit (test-data)) Represents the real test, but it takes too long to run.
  doit-parsed should result in the same thing."
  (= (doit (test-data)) (doit-parsed (test-data-parsed))))

(deftest regression
  (let [txs (time (e/doit-parsed (e/test-data-parsed)))]
    (is (= 616 (count txs)))
    ;; Type checks
    (is (true? (every? (fn [{:keys [transaction/amount
                                    transaction/currency
                                    transaction/date
                                    transaction/title
                                    transaction/tags
                                    transaction/type
                                    transaction/created-at]}]
                         (and (string? amount)
                              (string? (:currency/code currency))
                              (string? (:date/ymd date))
                              (string? title)
                              (vector? tags)
                              (some? type)
                              (integer? created-at)))
                       txs)))))
