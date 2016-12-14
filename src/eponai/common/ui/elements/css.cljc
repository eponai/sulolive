(ns eponai.common.ui.elements.css
  (:require
    [clojure.string :as s]
    [taoensso.timbre :refer [warn]]))

(def global-styles
  {::global-float-left  "float-left"
   ::global-float-right "float-right"})

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
   ::menu-text     "menu-text"})

(defn all-styles []
  (merge
    global-styles
    photo-styles
    menu-styles))

(defn keys->class
  "Convert keywords to string of classnames to use for :className in element options.

  Valid keywords are found in eponai.common.ui.elements.photo/all-styles"
  [styles]
  (let [all (all-styles)
        style-set (set styles)]
    (when-not (every? #(some? (get all %)) style-set)
      (let [unexpected (filter #(nil? (get all %)) style-set)]
        (warn "Ignoring unexpected CSS class keys " unexpected ". Available keys: " all)))

    (s/join " " (map #(get all %) style-set))))