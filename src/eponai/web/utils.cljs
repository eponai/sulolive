(ns eponai.web.utils
  (:require
    [taoensso.timbre :refer [debug]]))

(def breakpoints
  {:small   0
   :medium  640
   :large   1024
   :xlarge  1200
   :xxlarge 1440})

(defn bp-compare [bp other & [compare-fn]]
  (let [c (or compare-fn >)]
    (c (get breakpoints bp) (get breakpoints other))))

(defn breakpoint [size]
  (cond (> size 1440) :xxlarge
        (> size 1200) :xlarge
        (> size 1024) :large
        (> size 640) :medium
        :else :small))

(defn elements-by-class
  ([classname]
    (elements-by-class js/document classname))
  ([el classname]
   (array-seq (.getElementsByClassName el classname))))

(defn element-by-id [id]
  (.getElementById js/document id))

(defn elements-by-name [n]
  (array-seq (.getElementsByName n)))

(defn input-values-by-class
  ([classname]
    (input-values-by-class js/document classname))
  ([el classname]
   (map #(.-value %) (elements-by-class el classname))))

(defn first-input-value-by-class
  ([classname]
    (first-input-value-by-class js/document classname))
  ([el classname]
   (not-empty (first (input-values-by-class el classname)))))

