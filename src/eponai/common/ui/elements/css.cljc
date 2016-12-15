(ns eponai.common.ui.elements.css
  (:require
    [clojure.string :as s]
    [taoensso.timbre :refer [debug warn]]))

(def global-styles
  {::float-left      "float-left"
   ::float-right     "float-right"
   ::text-right      "text-right"
   ::vertical        "vertical"

   ::callout         "callout"

   ;; Menu
   ::menu            "menu"
   ::active          "active"
   ::menu-text       "menu-text"
   ::menu-dropdown   "menu-dropdown"

   ;; Grid
   ::row             "row"
   ::column          "column"

   ;; Photo
   ::photo           "photo"
   ::photo-container "photo-container"
   ::photo-square    "square"
   ::photo-thumbnail "thumbnail"
   ::photo-header    "header"
   ::photo-collage   "collage"
   ::photo-cover     "cover"})

(def breakpoints {:small "small" :medium "medium" :large "large" :xlarge "xlarge" :xxlarge "xxlarge"})
(def grid-cols 12)

(defn grid-styles []
  (reduce (fn [l [_ v]]
            (apply conj l (mapcat (fn [n]
                              [(keyword (str v "-" n))
                               (keyword (str v "-order-" n))])
                            (range 1 (inc grid-cols)))))
          #{} breakpoints))

(defn keys->classes
  [ks]
  (let [all global-styles
        style-set (set ks)]
    (when-not (every? #(some? (or (get all %)
                                  (contains? (grid-styles) (name %)))) style-set)
      (let [unexpected (filter #(nil? (get all %)) style-set)]
        (warn "Unexpected CSS class keys " unexpected ", using values " (map name unexpected) ".")))

    (map #(or (get all %) (name %)) style-set)))

(defn keys->class-str
  "Convert keywords to string of classnames to use for :className in element options.

  Valid keywords are found in eponai.common.ui.elements.photo/all-styles"
  [styles]
  (s/join " " (keys->classes styles)))

(defn add-class [class & [opts]]
  (update opts :classes conj class))

;; ----------- Basic ----------------------------------------------

(defn text-align [alignment & [opts]]
  (let [available #{:left :right :center}]
    (if (contains? available alignment)
      (add-class (keyword (str "text-" (name alignment))) opts)
      (do
        (warn "Ignoring text alignment CSS class with invalid alignment: " alignment ". Available keys are: " available)
        opts))))

(defn align [alignment & [opts]]
  (let [available #{:top :left :right :bottom :center :middle}]
    (if (contains? available alignment)
      (add-class (keyword (str (name :align) "-" (name alignment))) opts)
      (do
        (warn "Ignoring alignment CSS class with invalid alignment: " alignment ". Available keys are: " available)
        opts))))

(defn hide-for [{:keys [size only?]} & [opts]]
  (let [s (get breakpoints size)]
    (if (some? s)
      (let [c (str "hide-for-" s (when only? "-only"))]
        (add-class (keyword c) opts))
      (do
        (warn "Ignoring column visibility CSS class for invalid breakpoint: " size
              ". Available are: " (keys breakpoints))
        opts))))

(defn show-for [{:keys [size only?]} & [opts]]
  (let [s (get breakpoints size)]
    (if (some? s)
      (let [c (str "show-for-" s (when only? "-only"))]
        (add-class (keyword c) opts))
      (do
        (warn "Ignoring column visibility CSS class for invalid breakpoint: " size
              ". Available are: " (keys breakpoints))
        opts))))

(defn callout [& [opts]]
  (add-class ::callout opts))

;; ----------- Grid ---------------------------------------------

(defn grid-row [& [opts]]
  (add-class ::row opts))

(defn grid-row-columns [counts & [opts]]
  (let [class-fn (fn [[k v]]
                   (if (contains? breakpoints k)
                     (if (<= 1 v grid-cols)
                       (str (get breakpoints k) "-up-" v)
                       (warn "Ignoring row column count CSS class for invalid column count: " v
                             ". Available values are 1-" grid-cols))
                     (warn "Ignoring row column count CSS class for invalid breakpoint: " k
                           ". Available are: " (keys breakpoints))))]
    (reduce #(add-class %2 %1) opts (map class-fn counts))))

(defn grid-column [& [opts]]
  (add-class ::column opts))

(defn grid-column-size [sizes & [opts]]
  (let [class-fn (fn [[k v]]
                   (if (contains? breakpoints k)
                     (if (<= 1 v grid-cols)
                       (str (get breakpoints k) "-" v)
                       (warn "Ignoring column size CSS class for invalid column size: " v
                             ". Available values are 1-" grid-cols))
                     (warn "Ignoring column size CSS class for invalid breakpoint: " k
                           ". Available are: " (keys breakpoints)))) ]
    (reduce #(add-class %2 %1) opts (map class-fn sizes))))

(defn grid-column-order [orders & [opts]]
  (let [class-fn (fn [[k v]]
                   (if (contains? breakpoints k)
                     (if (<= 1 v grid-cols)
                       (str (get breakpoints k) "-order-" v)
                       (warn "Ignoring column order CSS class for invalid order value: " v
                             ". Available values are 1-" grid-cols))
                     (warn "Ignoring column order CSS class for invalid breakpoint: " k
                           ". Available are: " (keys breakpoints))))]
    (reduce (fn [m class] (add-class class m)) opts (map class-fn orders))))