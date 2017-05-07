(ns eponai.common.ui.elements.css
  (:require
    [clojure.string :as s]
    [taoensso.timbre :refer [debug warn]]))

(def global-styles
  {::float-left            "float-left"
   ::float-right           "float-right"
   ::text-right            "text-right"
   ::vertical              "vertical"

   ::icon                  "icon"

   ::callout               "callout"

   ;; Button
   ::button                "button"
   ::button-hollow         "hollow"

   ;; Color Styles
   ::color-primary         "primary"
   ::color-secondary       "secondary"
   ::color-success         "success"
   ::color-warning         "warning"
   ::color-alert           "alert"


   ;; Menu
   ::menu                  "menu"
   ::active                "active"
   ::menu-text             "menu-text"
   ::menu-dropdown         "menu-dropdown"
   ::tabs-title            "tabs-title"
   ::tabs                  "tabs"

   ;; Grid
   ::row                   "row"
   ::column                "column"

   ;; Photo
   ::photo                 "photo"
   ::photo-container       "photo-container"
   ::photo-full            "full"
   ::photo-square          "square"
   ::photo-circle          "circle"
   ::photo-thumbnail       "thumbnail"
   ::photo-header          "header"
   ::photo-collage         "collage"
   ::photo-cover           "cover"
   ::photo-overlay         "overlay"
   ::photo-overlay-content "content"})

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
        ;; To keep order:
        style-set (into [] (distinct) ks)]
    (when-not (every? #(or (some? (get all %))
                           (contains? (grid-styles) %)) style-set)
      (let [unexpected (filter #(or (nil? (get all %))
                                    (not (contains? (grid-styles) %))) style-set)]
        ;(warn "Unexpected CSS class keys " unexpected ", using values " (map #(when (some? %) (name %)) unexpected) ".")
        ))

    (map #(or (get all %) (when (some? %) (name %))) style-set)))

(defn keys->class-str
  "Convert keywords to string of classnames to use for :className in element options.

  Valid keywords are found in eponai.common.ui.elements.photo/all-styles"
  [styles]
  (s/join " " (keys->classes styles)))

(defn add-class [class & [opts]]
  (update opts :classes conj class))

(defn add-classes [classes & [opts]]
  (update opts :classes into classes))

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

(defn hide-for [size & [opts]]
  (let [s (get breakpoints size)]
    (if (some? s)
      (let [c (str "hide-for-" s)]
        (add-class (keyword c) opts))
      (do
        (warn "Ignoring column visibility CSS class for invalid breakpoint: " size
              ". Available are: " (keys breakpoints))
        opts))))

(defn show-for [size & [opts]]
  (let [s (get breakpoints size)]
    (if (some? s)
      (let [c (str "show-for-" s)]
        (add-class (keyword c) opts))
      (do
        (warn "Ignoring column visibility CSS class for invalid breakpoint: " size
              ". Available are: " (keys breakpoints))
        opts))))

(defn show-for-sr [& [opts]]
  (add-class :show-for-sr opts))

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
                             ". Available values are " 1 "-" grid-cols))
                     (warn "Ignoring column size CSS class for invalid breakpoint: " k
                           ". Available are: " (keys breakpoints))))]
    (reduce #(add-class %2 %1) opts (map class-fn sizes))))

(defn grid-column-order [orders & [opts]]
  (let [class-fn (fn [[k v]]
                   (if (contains? breakpoints k)
                     (if (<= 1 v grid-cols)
                       (str (get breakpoints k) "-order-" v)
                       (warn "Ignoring column order CSS class for invalid order value: " v
                             ". Available values are " 1 "-" grid-cols))
                     (warn "Ignoring column order CSS class for invalid breakpoint: " k
                           ". Available are: " (keys breakpoints))))]
    (reduce (fn [m class] (add-class class m)) opts (map class-fn orders))))

(defn grid-column-offset [offsets & [opts]]
  (let [class-fn (fn [[k v]]
                   (if (contains? breakpoints k)
                     (if (<= 0 v grid-cols)
                       (str (get breakpoints k) "-offset-" v)
                       (warn "Ignoring column offset CSS class for invalid order value: " v
                             ". Available values are " 0 "-" grid-cols))
                     (warn "Ignoring column offset CSS class for invalid breakpoint: " k
                           ". Available are: " (keys breakpoints))))]
    (reduce (fn [m class] (add-class class m)) opts (map class-fn offsets))))


;; ------------------------------- CLearfix

(defn clearfix [& opts]
  (add-class ::clearfix opts))
;; ---------------------------------- Button ------------------------------

(defn button [& [opts]]
  (add-class ::button opts))

(defn button-hollow [& [opts]]
  (button (add-class ::button-hollow opts)))

(defn expanded [& [opts]]
  (add-class :expanded opts))

(defn add-color-style [color & [opts]]
  (let [names (clojure.string/split (name color) "-")]
    (when (not= (first names) "color")
      (warn "Adding an unexpected style to button: " color))
    (button (add-class color opts))))