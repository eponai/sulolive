(ns eponai.common.ui.elements.css
  (:require
    [clojure.string :as s]
    [taoensso.timbre :refer [debug warn]]))

(def global-styles
  {::global-float-left  "float-left"
   ::global-float-right "float-right"})

(defn- calc-grid-styles []
  (let [breakpoints ["small" "medium" "large" "xlarge"]
        prefix :grid-column
        cols 12

        ret (reduce (fn [m bp]
                  (let [str-vals (map #(str (name bp) "-" %) (range 1 (inc cols)))
                        ks (map #(keyword
                                  (str 'eponai.common.ui.elements.css)
                                  (str (name prefix) "-" %))
                                str-vals)]
                    (merge m
                           (zipmap ks str-vals)))) {} breakpoints)]
    ret))

(def grid-styles
  (merge
    {::grid-row              "row"
     ::grid-row-align-middle "align-middle"
     ::grid-column           "column"}
    (calc-grid-styles)))

(def photo-styles
  {::photo     "photo"
   ::photo-container "photo-container"
   ::photo-square    "square"
   ::photo-thumbnail "thumbnail"
   ::photo-header    "header"
   ::photo-collage   "collage"
   ::photo-cover     "cover"})

(def menu-styles
  {::menu          "menu"
   ::menu-vertical "vertical"
   ::menu-active   "active"
   ::menu-text     "menu-text"
   ::menu-dropdown "menu-dropdown"})

(def cart-styles
  {::cart "cart"})

(def store-styles
  {::store-main-menu "store-main-menu"
   ::store-container "store-container"})

(defn all-styles []
  (merge
    global-styles
    grid-styles
    photo-styles
    menu-styles
    cart-styles
    store-styles))

(defn keys->classes
  [ks]
  (let [all (all-styles)
        style-set (set ks)]
    (when-not (every? #(some? (get all %)) style-set)
      (let [unexpected (filter #(nil? (get all %)) style-set)]
        (warn "Unexpected CSS class keys " unexpected ", using values " (map name unexpected) ". Available keys: " (keys all))))

    (map #(or (get all %) (name %)) style-set)))

(defn keys->class-str
  "Convert keywords to string of classnames to use for :className in element options.

  Valid keywords are found in eponai.common.ui.elements.photo/all-styles"
  [styles]
  (s/join " " (keys->classes styles)))