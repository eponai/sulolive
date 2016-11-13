(ns eponai.web.ui.project.settings
  (:require
    [eponai.client.parser.message :as message]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]
    [eponai.web.ui.utils :as utils]))

(defui ProjectSettings
  static om/IQuery
  (query [_]
    [{:query/active-project [:ui.component.project/active-project]}
     {:query/all-categories [:category/name]}
     {:query/project-users [:user/email]}])
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
  (save-category [this]
    (let [{:keys [input-category]} (om/get-state this)
          {:keys [project]} (om/get-computed this)
          category-name (clojure.string/trim input-category)]
      (debug "Save category: " {:category/name input-category
                                :category/project (:db/id project)})
      (when-not (empty? category-name)
        (om/transact! this `[(project.category/save ~{:category {:category/name    category-name
                                                                 :category/project (:db/id project)}})
                             :query/all-categories])
        (om/update-state! this assoc :add-category? false))))
  (render [this]
    (let [{:keys [query/all-categories
                  query/project-users]} (om/props this)
          {:keys [add-category? input-category]} (om/get-state this)]
      (html
        [:div#project-settings
         [:div.content-section
          [:div.row.section-title
           [:span "Categories"]]
          [:div.row
           [:ul.menu.all-categories
            (if (empty? all-categories)
              [:p "You have no categories yet"]
              (map (fn [c]
                     [:li
                      {:key (:category/name c)}
                      [:a.button.black
                       (:category/name c)]])
                   all-categories))
            [:li.add-category-section
             [:a.button.hollow.secondary
              {:on-click #(om/update-state! this assoc :add-category? true)}
              "+ Create"]
             (when add-category?
               (utils/popup
                 {:on-close #(om/update-state! this assoc :add-category? false)}
                 [:div.add-category-input
                  [:input
                   {:value       (or input-category "")
                    :placeholder "Category name..."
                    :type        "text"
                    :on-change   #(om/update-state! this assoc :input-category (.. % -target -value))}]
                  [:a.button.hollow.black
                   {:on-click #(.save-category this)}
                   "Save"]]))]]]]

         ;[:div.content-section
         ; [:div.row.section-title
         ;  [:span "Users"]]
         ; [:div.row
         ;  (map (fn [u]
         ;         [:a.button.hollow.black
         ;          {:key (str (:db/id u))}
         ;          (:user/email u)])
         ;       project-users)]]


         [:div.content-section
          ;[:div.row.section-title
          ; [:span "Delete Project"]]
          [:div.row
           [:div.column
            [:span
             "Delete this project with all its data. Recorded expenses and incomes will be deleted as well as any created categories."]]
           [:div.column.small-4
            [:a.button.secondary.expanded.hollow.delete-button
             {:on-click #(.delete-project this)}
             "Delete Project"]]]]]))))

(def ->ProjectSettings (om/factory ProjectSettings))