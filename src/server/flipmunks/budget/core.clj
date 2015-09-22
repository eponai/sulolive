(ns flipmunks.budget.core
  (:gen-class)
  (:require clojure.set))

(defn filter-map-keys [m]
  (->> m
       (filter #(map? (second %)))
       (map first)
       (into #{})))

(defn key-intersection [maps]
  (->> maps
       (map filter-map-keys)
       (apply clojure.set/intersection)))

(defn deep-merge [maps]
  (let [map-a (first maps)
        map-b (second maps)
        red (fn [m me]
              (let [k (key me)
                    v (val me)]
                (if (map? v)
                  (assoc m k (deep-merge [(k m) v]))
                  (assoc m k v))))]
    (reduce red map-a map-b)))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))