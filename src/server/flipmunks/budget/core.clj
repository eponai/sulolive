(ns flipmunks.budget.core
  (:gen-class)
  (:require clojure.set))

(defn deep-merge [& maps]
  (let [merge-entry (fn [m [k v]]
                      (if (and (map? v))                    ; might need to check if (get m k) is a map as well
                        (assoc m k (deep-merge (get m k) v))
                        (assoc m k v)))
        merge1 (fn [m1 m2]
                 (reduce merge-entry m1 m2))]
    (reduce merge1 maps)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))