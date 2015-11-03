(ns flipmunks.budget.ui.tag_dc
  (:require [devcards.core :as dc]
            [flipmunks.budget.ui.tag :as t]
            [flipmunks.budget.ui :refer [style]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]])
  (:require-macros [devcards.core :refer [defcard]]))

(defn tag-props
  ([name] (tag-props name nil))
  ([name delete-fn]
   (merge {:tag/name name}
          (when delete-fn
            {::t/edit-mode true
             ::t/delete-fn delete-fn}))))

(defcard tag-with-name
         (t/tag (tag-props "Tag name")))

(defui DeletableTag
       Object
       (render [this]
               (let [{:keys [::tag-hidden]} (om/get-state this)]
                 (html
                   [:div
                    [:div (style {:display       (if tag-hidden "none" "block")
                                  :margin-bottom "1em"})
                     (t/tag
                       (tag-props "with delete"
                                  #(om/update-state! this assoc ::tag-hidden true)))]
                    [:button {:on-click #(om/update-state! this assoc ::tag-hidden false)}
                     "make tag visible"]]))))

(defcard tag-in-edit-mode--with-delete-button
         ((om/factory DeletableTag)))