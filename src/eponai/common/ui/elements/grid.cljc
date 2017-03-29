(ns eponai.common.ui.elements.grid
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [warn]]))

(def grid-cols 12)
(defn row [opts & content]
  (dom/div (css/grid-row opts) content))

(defn column [opts & content]
  (dom/div (css/grid-column opts) content))

(defn row-column [opts & content]
  (dom/div (->> (css/grid-row opts) css/grid-column) content))

(defn column-size [sizes & [opts]]
  (let [class-fn (fn [[k v]]
                   (if (contains? css/breakpoints k)
                     (if (<= 1 v css/grid-cols)
                       (str (get css/breakpoints k) "-" v)
                       (warn "Ignoring column size CSS class for invalid column size: " v
                             ". Available values are " 1 "-" css/grid-cols))
                     (warn "Ignoring column size CSS class for invalid breakpoint: " k
                           ". Available are: " (keys css/breakpoints))))]
    (reduce #(css/add-class %2 %1) opts (map class-fn sizes))))

(defn column-offset [offsets & [opts]]
  (let [class-fn (fn [[k v]]
                   (if (contains? css/breakpoints k)
                     (if (<= 0 v grid-cols)
                       (str (get css/breakpoints k) "-offset-" v)
                       (warn "Ignoring column offset CSS class for invalid order value: " v
                             ". Available values are " 0 "-" grid-cols))
                     (warn "Ignoring column offset CSS class for invalid breakpoint: " k
                           ". Available are: " (keys css/breakpoints))))]
    (reduce (fn [m class] (css/add-class class m)) opts (map class-fn offsets))))

(defn column-order [orders & [opts]]
  (let [class-fn (fn [[k v]]
                   (if (contains? css/breakpoints k)
                     (if (<= 1 v grid-cols)
                       (str (get css/breakpoints k) "-order-" v)
                       (warn "Ignoring column order CSS class for invalid order value: " v
                             ". Available values are " 1 "-" grid-cols))
                     (warn "Ignoring column order CSS class for invalid breakpoint: " k
                           ". Available are: " (keys css/breakpoints))))]
    (reduce (fn [m class] (css/add-class class m)) opts (map class-fn orders))))

(defn columns-in-row [counts & [opts]]
  (let [class-fn (fn [[k v]]
                   (if (contains? css/breakpoints k)
                     (if (<= 1 v grid-cols)
                       (str (get css/breakpoints k) "-up-" v)
                       (warn "Ignoring row column count CSS class for invalid column count: " v
                             ". Available values are 1-" grid-cols))
                     (warn "Ignoring row column count CSS class for invalid breakpoint: " k
                           ". Available are: " (keys css/breakpoints))))]
    (reduce #(css/add-class %2 %1) opts (map class-fn counts))))

;; Custom grids

(defn products [ps el-fn]
  (row
    (columns-in-row {:small 2 :medium 3})
    (map
      (fn [p]
        (column
          nil
          (el-fn p)))
      ps)))