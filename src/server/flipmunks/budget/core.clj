(ns flipmunks.budget.core
  (:gen-class)
  (:require clojure.set))

(defn deep-merge [& maps]
  (let [merge-entry (fn [m me]
                      (let [k (key me)
                            v (val me)]
                        (if (map? v)
                          (assoc m k (deep-merge (k m) v))
                          (assoc m k v))))
        merge1 (fn [m1 m2]
                 (reduce merge-entry m1 m2))]
    (reduce merge1 maps)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))