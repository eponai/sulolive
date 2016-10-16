(ns eponai.web.ui.project.settings
  (:require
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]))

(defui ProjectSettings
  static om/IQuery
  (query [_]
    [{:query/active-project [:ui.component.project/active-project]}])
  Object
  (render [this]
    (html
      [:div#project-settings
       [:div.content-section
        [:div.row.section-title
         [:span "Users"]]]
       [:div.content-section
        [:div.row.section-title
         [:span "Categories"]]]
       [:div.content-section
        [:div.row
         [:div.column.small-4.small-offset-4
          [:a.button.alert.expanded
           "Delete Project"]]]]])))

(def ->ProjectSettings (om/factory ProjectSettings))