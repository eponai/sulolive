(ns budget.core-test
  (:require [clojure.test :refer :all]
            [flipmunks.budget.core :refer :all]))

(deftest a-test
  (testing "deep merge"
    (is (= (deep-merge {:a {:b 1 :c 2}} {:a {:d 1}}) {:a {:b 1 :c 2 :d 1}}))))
