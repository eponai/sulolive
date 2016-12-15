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
        ;; To keep order:
        style-set (into [] (distinct) ks)]
    (when-not (every? #(some? (or (get all %)
                                  (contains? (grid-styles) (name %)))) style-set)
      (let [unexpected (filter #(nil? (get all %)) style-set)]
        (warn "Unexpected CSS class keys " unexpected ", using values " (map name unexpected) ". Available keys: " (keys all))))

    (map #(or (get all %) (name %)) style-set)))

(defn keys->class-str
  "Convert keywords to string of classnames to use for :className in element options.

  Valid keywords are found in eponai.common.ui.elements.photo/all-styles"
  [styles]
  (s/join " " (keys->classes styles)))

(defn add-class [class opts]
  (update opts :classes conj class))

(defn text-right [& [opts]]
  (add-class ::text-right opts))

(defn grid-row [& [opts]]
  (add-class ::row opts))

(defn grid-sizes [sizes & [opts]]
  (reduce (fn [m class]
            (add-class class m))
          opts
          (map (fn [[k v]]
                 (when (contains? breakpoints k)
                   (str (get breakpoints k) "-" v)))
               sizes)))

(defn grid-orders [orders & [opts]]
  (reduce (fn [m class]
            (add-class class m))
          opts
          (map (fn [[k v]]
                 (when (contains? breakpoints k)
                   (str (get breakpoints k) "-order-" v)))
               orders)))

(defn align [alignment & [opts]]
  (add-class (keyword (str (name :align) "-" (name alignment))) opts))

(defn grid-column [& [opts]]
  (add-class ::column opts))

(defn callout [& [opts]]
  (add-class ::callout opts))