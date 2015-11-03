(ns flipmunks.budget.ui.tag_dc
  (:require [devcards.core :as dc]
            [flipmunks.budget.ui.tag :as t]
            [flipmunks.budget.ui :refer [style]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]])
  (:require-macros [devcards.core :refer [defcard]]))

(defcard tag-with-name
         (t/tag {:tag/name "Tag name"}))

(defui DeletableTag
       Object
       (render [this]
               (let [{:keys [::tag-hidden]} (om/get-state this)]
                 (html
                   [:div
                    [:div (style {:display       (if tag-hidden "none" "block")
                                  :margin-bottom "1em"})
                     (t/tag {:tag/name     "with delete"
                             ::t/edit-mode true
                             ::t/delete-fn #(om/update-state! this assoc ::tag-hidden true)})]
                    [:button {:on-click #(om/update-state! this assoc ::tag-hidden false)}
                     "make tag visible"]]))))

(defcard tag-in-edit-mode--with-delete-button
         ((om/factory DeletableTag)))