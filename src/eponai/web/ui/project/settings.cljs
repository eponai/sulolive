(ns eponai.web.ui.project.settings
  (:require
    [eponai.client.parser.message :as message]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]))

(defui ProjectSettings
  static om/IQuery
  (query [_]
    [{:query/active-project [:ui.component.project/active-project]}])
  Object
  (delete-project [this]
    (let [{:keys [query/active-project]} (om/props this)
          _ (debug "Deleting active project: " active-project)
          message-id (message/om-transact! this `[(project/delete ~{:project-dbid (:ui.component.project/eid active-project)})
                                       :query/all-projects
                                       :query/active-project])]
      (om/update-state! this assoc :pending-transaction message-id)))

  (componentDidUpdate [this _ _]
    (when-let [history-id (:pending-transaction (om/get-state this))]
      (let [{:keys [query/message-fn]} (om/props this)
            {:keys [on-close]} (om/get-computed this)
            message (message-fn history-id 'project/delete)]
        (debug "Deleted project!")
        (when message
          (js/alert (message/message message))
          (when on-close
            (on-close))))))
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
          [:a.button.alert.expanded.hollow
           {:on-click #(.delete-project this)}
           "Delete Project"]]]]])))

(def ->ProjectSettings (om/factory ProjectSettings))