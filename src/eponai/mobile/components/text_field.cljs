(ns eponai.mobile.components.text-field
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [text-input]]))

(defn text-field [options]
  (text-input (opts
                (merge {
                        ;:onChangeText   on-change-text
                        ;:keyboardType   "number-pad"
                        ;:value          value
                        ;:placeholder    "0.00"
                        ;:autoCapitalize "none"
                        :style          {:height 44 :border-color :gray :border-width 1 :border-radius 3 :padding 10 :background-color :white :margin-bottom 20 :flex 1}}
                       options))))
