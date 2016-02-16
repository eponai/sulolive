(ns eponai.client.ui.utils
  (:require [eponai.client.ui :refer-macros [opts]]))

;;;;;;; UI component helpers

(defn loader []
  [:div.loader-circle-black
   (opts {:style {:top      "50%"
                  :left     "50%"
                  :position "fixed"
                  :z-index  1050}})])

(defn click-outside-target [on-click]
  [:div#click-outside-target
   (opts {:style    {:top      0
                     :bottom   0
                     :right    0
                     :left     0
                     :position "fixed"}
          :on-click on-click})])

(defn modal [{:keys [content on-close]}]
  [:div
   (opts {:style {:top              0
                  :bottom           0
                  :right            0
                  :left             0
                  :position         "fixed"
                  :z-index          1050
                  :opacity          1
                  :background       "rgba(0,0,0,0.6)"
                  :background-color "#123"
                  :display          "block"}})
   (click-outside-target on-close)

   [:div.modal-dialog
    [:div.modal-content
     content]]])