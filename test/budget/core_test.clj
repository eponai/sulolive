(ns budget.core-test
  (:require [clojure.test :refer :all]
            [flipmunks.budget.core :refer :all]))

(deftest a-test
  (testing "deep merge"
    (is (= (deep-merge {:a 1} {:b 2}) {:a 1 :b 2}))
    (is (= (deep-merge {:a 1} {:a 2}) {:a 2}))
    (is (= (deep-merge {:a 1}) {:a 1}))))
