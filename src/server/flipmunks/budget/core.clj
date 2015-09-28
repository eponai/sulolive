(ns flipmunks.budget.core
  (:gen-class)
  (:require clojure.set))

; implement depth and apply merge-with for the maximum found depth. Recur on depth for one map and return.
(defn depth [m]
  (loop [d 0
         inner-m m]
    ))

(defn max-depth [& maps]
  (max (map depth maps))
  )

(defn deep-merge1 [m [k v]]

  )

(defn deep-merge
  "Returns a map that consists of the rest of the maps merged onto the first.\n
  If a key occurs in more than one map, the mapping from\n  the latter (left-to-right) will be the mapping in the result.\n
  If a key holding a map value occurs in more than one map, the mapping(s) from the latter (left-to-right)\n
  will be combined with the mapping in the result by calling (deep-merge val-in-result val-in-latter)."
  [& maps]
  (when (some identity maps)
    (let [merge-entry (fn [m [k v]]
                        (if (map? v)                     ; might need to check if (get m k) is a map as well
                          (assoc m k (deep-merge (get m k) v))
                          (assoc m k v)))
          merge1 (fn [m1 m2]
                   (reduce merge-entry m1 m2))]
      (reduce merge1 maps))))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))