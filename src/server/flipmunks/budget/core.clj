(ns flipmunks.budget.core
  (:gen-class)
  (:require clojure.set))

(defn filter-map-keys [m]
  (into #{} (map first (filter #(map? (second %)) m))))

(defn key-intersection [maps]
  (apply clojure.set/intersection (map filter-map-keys maps)))














(comment

  (declare deep-merge)
  (defn merge-entry [m e]
    (let [k (key e)
          v (val e)]
      (if (and (contains? m k) (map? v))
        (do
          (println m)
          (assoc m k (deep-merge (get m k))))
        (do
          (println "else")
          (assoc m k v)))))

  (defn deep-merge [maps]
    ;(println maps)
    (reduce (reduce merge-entry (first maps) (seq (second maps))) maps)))

(comment
  (defn deep-merge [maps]
    (let [k-shared (key-intersection maps)]
      (if (empty? k-shared)
        (merge maps)
        (deep-merge (merge-with merge maps))
        ;(deep-merge (map #(map % maps) k-shared))
        ))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))