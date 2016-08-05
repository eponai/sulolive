(ns eponai.mobile.components.button
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [touchable-highlight text]]))

(defn primary [{:keys [title on-press]}]
  (touchable-highlight
    (opts {:onPress on-press
           :style   {:background-color "#044e8a" :padding 10 :border-radius 5 :height 44 :justify-content "center" :margin-vertical 5}})
    (text
      (opts {:style {:color "white" :text-align "center" :font-weight "bold"}})
      (or title ""))))

