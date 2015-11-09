(ns flipmunks.budget.ui.modal
  (:require [om.next :as om :refer-macros [defui]]
            [flipmunks.budget.ui :refer [style]]
            [sablono.core :as html :refer-macros [html]]
            [garden.core :refer [css]]))

(defui Modal
  Object
  (render
    [this]
    (let [{:keys [dialog-fn]} (om/props this)]
      (html
        [:div
         [:style (css [:#modaldialog
                       {:top              0
                        :bottom           0
                        :right            0
                        :left             0
                        :position         "fixed"
                        :z-index          99999
                        :opacity          1
                        :background "rgba(0,0,0,0.6)"
                        :background-color "#123"}
                       [:div {:width         "40em"
                              :position      :relative
                              :opacity       1
                              :margin        "10% auto"
                              :padding       "5px 20px 13px 20px"
                              :border-radius "10px"
                              :background-color    "#eee"
                              :color "000"}]])]
         [:div#modaldialog
          (dialog-fn)]]))))

(def ->Modal (om/factory Modal))