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
  (loop [m (gen/map (gen/choose 0 1) gen/int)
         level n]
    (if (> level 0)
      (recur (gen/map (gen/choose 0 1) m) (dec level))
      m)))

(defn n-level-merge [n]
  (loop [fn merge
         level n]
    (if (> level 0)
      (recur (partial merge-with fn) (dec level))
      fn)))

(defspec n-level-maps-are-merged
         50
         (prop/for-all [[n maps] (gen/bind gen/nat (fn [level]
                                                     (println level)
                                                     (gen/bind (gen/vector (gen-n-level-map level) 0 3)
                                                               (fn [v]
                                                                 (gen/return [level v])))))]
                       ;(println n maps)
                       (= (apply (n-level-merge n) maps)
                          (apply deep-merge maps))))
