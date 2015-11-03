(ns flipmunks.budget.ui.tag
  (:require [om.next :as om :refer-macros [defui]]
            [flipmunks.budget.ui :refer [style]]
            [garden.core :refer [css]]
            [sablono.core :refer-macros [html]]))

(defui Tag
       static om/IQuery
       (query [this] [:tag/name])
       Object
       (render
         [this]
         (html [:div (style {:display "inline-block"})
                [:style (css [:#ui-transaction-tag {:display        "inline-block"
                                                    :padding        "0.2em 0.2em"
                                                    :margin         "0.2em"
                                                    :border-width   "1px"
                                                    :border-style   "solid"
                                                    :border-radius  "0.3em"
                                                    :text-transform "capitalize"
                                                    :font-size      "1em"
                                                    :border-color   "#ddd"
                                                    :cursor         "default"}
                              [:&:hover {:border-color "#aaa"}]
                              [:&:active {:border-color "#ddd"}]])]
                [:div {:id       "ui-transaction-tag"
                       :on-click #(prn %)}
                 (:tag/name (om/props this))]])))

(def tag (om/factory Tag))
