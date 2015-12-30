(ns eponai.devcards.ui.tag_dc
  (:require [devcards.core :as dc]
            [eponai.client.ui.tag :as t]
            [eponai.client.ui :refer-macros [style]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]])
  (:require-macros [devcards.core :refer [defcard]]))

(defcard tag-with-name
         (t/->Tag (t/tag-props "Tag name")))

(defui DeletableTag
       Object
       (render [this]
               (let [{:keys [::tag-hidden]} (om/get-state this)]
                 (html
                   [:div
                    [:div (style {:display       (if tag-hidden "none" "block")
                                  :margin-bottom "1em"})
                     (t/->Tag
                       (t/tag-props "with delete"
                                  #(om/update-state! this assoc ::tag-hidden true)))]
                    [:button {:on-click #(om/update-state! this assoc ::tag-hidden false)}
                     "make tag visible"]]))))

(defcard tag-in-edit-mode--with-delete-button
         ((om/factory DeletableTag)))
