(ns flipmunks.budget.ui
  (:require [clojure.walk :as w]
            [clojure.string :as s]))

(defn ->camelCase [k]
  (when (namespace k)
    (throw (str "cannot camelCase a keyword with a namespace. key=" k)))
  (let [[a & xs] (s/split (name k) "-")]
    (s/join (cons a (map s/capitalize xs)))))

;; Using memoize, since the number of possible keys is limited to css keys
(let [camel-case (memoize ->camelCase)]
  ;; TODO: Make this a macro, so that the transformations are made in compile time
  (defn style [style-map]
   (let [camelCased (w/postwalk (fn [x] (if (keyword? x) (camel-case x) x))
                                style-map)]
     {:style (clj->js camelCased)})))
