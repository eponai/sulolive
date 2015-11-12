(ns flipmunks.budget.ui.modal
  (:require [om.next :as om :refer-macros [defui]]
            [flipmunks.budget.ui :refer [style]]
            [sablono.core :as html :refer-macros [html]]
            [garden.core :refer [css]]))

(defui Modal
  Object
  (render
    [this]
    (let [{:keys [dialog-content on-close title]} (om/props this)]
      (html
        [:div
         [:style (css [:#modal
                       {:top              0
                        :bottom           0
                        :right            0
                        :left             0
                        :position         "fixed"
                        :z-index          1050
                        :opacity          1
                        :background       "rgba(0,0,0,0.6)"
                        :background-color "#123"}
                       [:div#modal-dialog
                        {:position         :relative
                         :opacity          1
                         :margin           "10% auto"
                         :max-width        "400px"
                         :padding          "5px 20px 13px 20px"
                         :border-radius    "10px"
                         :background-color "#eee"
                         :color            "000"}]])]
         [:div#modal
          [:div#click-outside-target (merge (style {:top      0
                                                    :bottom   0
                                                    :right    0
                                                    :left     0
                                                    :position "fixed"})
                                            {:on-click on-close})]
          [:div#modal-dialog
           [:div#modal-header
            [:button {:type "button" :class "close" :on-click on-close} "x"]]
            [:h4 {:class "modal-title"} title]
           [:div#modal-content (dialog-content)]]]]))))

(def ->Modal (om/factory Modal))