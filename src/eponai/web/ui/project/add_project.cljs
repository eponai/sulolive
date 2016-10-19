(ns eponai.web.ui.project.add-project
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]))

(defui NewProject
  Object
  (render [this]
    (let [{:keys [on-close on-save]} (om/get-computed this)
          {:keys [input-name]} (om/get-state this)]
      (html
        [:div#add-project
         [:h4.header
          "Add project"]
         [:div.top-bar-container]
         [:div.content
          [:div.content-section
           [:div.row
            [:div.column
             [:input
              (opts
                {:value       (or input-name "")
                 :placeholder "Project name"
                 :type        "text"
                 :on-change   #(om/update-state! this assoc :input-name (.-value (.-target %)))
                 :style       {:width "100%"}})]]
            [:div.column.small-3
             [:a.button.hollow.expanded
              {:on-click #(do
                           (on-save input-name)
                           (on-close))}
              "Save"]]]]
          ;[:br]
          ;[:div.content-section.clearfix
          ; [:a.button.hollow.float-right
          ;  {:on-click #(do
          ;               (on-save input-name)
          ;               (on-close))}
          ;  "Save"]]
          ]]))))

(def ->NewProject (om/factory NewProject))
