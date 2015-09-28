(ns budget.core-test
  (:require [clojure.test :refer :all]
            [flipmunks.budget.core :refer :all]
            [clojure.test.check.clojure-test :refer :all]
            [clojure.test.check :refer :all]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))


(deftest a-test
  (testing "deep merge"
    (is (= (deep-merge  {:a 1}) {:a 1}))
    (is (= (deep-merge  {:a 1} {:b 2}) {:a 1 :b 2}))
    (is (= (deep-merge  {:a 1 :b {:c 1}} {:b {:d 4}}) {:a 1 :b {:c 1 :d 4}}))
    (is (= (deep-merge  {:a {:b {:c 2}}} {:a {:b {:d 3}}} {:a {:e 1 :b {:d 2}}}) {:a {:b {:c 2 :d 2} :e 1}}))

    (is (= (deep-merge {:currency :USD
                        :dates {2015 {1 {3 {:purchases [{:name "coffee" :cost {:currency :LEK :price 150}}]
                                            :rates {:LEK 0.0081
                                                    :SEK 0.1213}}}}}}
                       {:currency :USD
                        :dates {2015 {1 {4 {:purchases [{:name "coffee" :cost {:currency :LEK :price 150}}, {:name "market" :cost {:currency :LEK :price 300}}]
                                            :rates {:LEK 0.0081
                                                    :SEK 0.1213}}}}}}))
        {:currency :USD
         :dates {2015 {1 {3 {:purchases [{:name "coffee" :cost {:currency :LEK :price 150}}]
                             :rates {:LEK 0.0081
                                     :SEK 0.1213}}
                          4 {:purchases [{:name "coffee" :cost {:currency :LEK :price 150}}, {:name "market" :cost {:currency :LEK :price 300}}]
                             :rates {:LEK 0.0081
                                     :SEK 0.1213}}}}}})))

(defn gen-n-level-map [n]
  (loop [m (gen/map gen/keyword gen/int)
         level n]
    (if (> level 0)
      (recur (gen/map gen/keyword m) (dec level))
      m)))

(defspec one-level-maps-are-merged
         50
         (prop/for-all [maps (gen/vector (gen-n-level-map 0) 0 10)]
                       (= (apply merge maps)
                          (apply deep-merge maps))))

(defspec two-level-maps-are-merged
         10
         (prop/for-all [maps (gen/vector (gen-n-level-map 1) 0 10)]
                       (= (apply merge-with merge maps)
                          (apply deep-merge maps))))

(defspec three-level-maps-are-merged
         10
         (prop/for-all [maps (gen/vector (gen-n-level-map 2) 0 10)]
                       (= (apply merge-with #(merge-with merge) maps)
                          (apply deep-merge maps))))