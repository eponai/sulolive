(ns eponai.mobile.ios.style
  (:require [eponai.client.ui :as ui :refer-macros [camel]]))

;; Need some kind of global styling.
(def style
  (camel {:nav-bar            {:style {:height 64}}
          :nav-bar->container {:style {:margin-top 64}}
          :container          {:style {:flex-direction "column" :margin 40 :align-items "center"}}
          :title              {:style {:font-size 30 :font-weight "100" :margin-bottom 20 :text-align "center"}}
          :row                {:style {:flex-direction "column" :align-items "flex-start"}}
          :row-text           {:style {:font-size 17 :margin-right 10}}
          :row-input          {:style {:height 36 :width 200 :border-width 1 :border-radius 5 :border-color "#cccccc"}}
          :save               {:style {:background-color "#999" :padding 10 :border-radius 5}}
          :save-text          {:style {:color "white" :text-align "center" :font-weight "bold"}}}))

(def style-sheet
  {:text-input {:padding 10}
   :header {:font-size 30 :font-weight "100" :margin-bottom 20 :text-align "center"}})

(defn text-input []
  (:text-input style-sheet))

(defn header []
  (:header style-sheet))

(defn mergeduce [xform coll]
  (transduce xform merge {} coll))

(defn styles [k-or-ks & maps]
  {:pre [(or (keyword? k-or-ks) (vector? k-or-ks))]}
  (merge
    (mergeduce (comp (map ui/camel*)) maps)
    (mergeduce (comp (map ui/->camelCase)
                     (map keyword)
                     (map style))
               (if (keyword? k-or-ks)
                 [k-or-ks]
                 k-or-ks))))
