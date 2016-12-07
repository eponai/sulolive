(ns eponai.web.utils)

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