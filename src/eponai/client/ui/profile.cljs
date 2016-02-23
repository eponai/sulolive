(ns eponai.client.ui.profile
  (:require [eponai.client.ui :refer-macros [opts]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [cljsjs.react-grid-layout]
            [eponai.client.routes :as routes]))

(defn generate-layout [budgets]
  (map (fn [budget i]
         {:x (mod i 4)
          :y (int (/ i 4))
          :w 1
          :h 1
          :i (str (:budget/uuid budget))})
       budgets (range)))

(defui Profile
  static om/IQuery
  (query [_]
    [{:query/current-user [:user/uuid
                           :user/email
                           :user/picture]}
     {:query/all-budgets [:budget/uuid
                          :budget/name
                          {:transaction/_budget [:transaction/uuid
                                                 {:transaction/tags [:tag/name]}]}]}])
  Object
  (update-grid-layout [this]
    (let [WidthProvider (.-WidthProvider (.-ReactGridLayout js/window))
          grid-element (WidthProvider (.-Responsive (.-ReactGridLayout js/window)))]
      (om/update-state! this assoc :grid-element grid-element)))

  (componentWillReceiveProps [this new-props]
    (let [budgets (:query/all-budgets new-props)]
      (om/update-state! this assoc :layout (clj->js (generate-layout budgets)))))

  (componentDidMount [this]
    (let [budgets (:query/all-budgets (om/props this))
          sidebar (.getElementById js/document "sidebar-wrapper")]
      (.addEventListener sidebar "transitionend" #(.update-grid-layout this))
      (.update-grid-layout this)
      (om/update-state! this
                        assoc
                        :layout (clj->js (generate-layout budgets)))))

  (render [this]
    (let [{:keys [query/current-user
                  query/all-budgets]} (om/props this)
          {:keys [layout grid-element]} (om/get-state this)
          React (.-React js/window)]
      (prn "Layout " layout)
      (html
        [:div

         [:div
          (opts {:style {:display        :flex
                         :flex-direction :row
                         :align-items    :center}})
          [:img.img-circle
           (opts {:src (:user/picture current-user)})]
          [:h1 (:user/email current-user)]]

         (when layout
           (.createElement React
                           grid-element
                           #js {:className        "layout",
                                :layouts          #js {:lg layout :md layout}
                                :rowHeight        300,
                                :cols             #js {:lg 3 :md 2 :sm 2 :xs 1 :xxs 1}
                                :useCSSTransforms true
                                :isDraggable      false
                                :isResizable      false
                                :onResizeStop     #(.forceUpdate this)}

                           (clj->js
                             (map
                               (fn [budget-props]
                                 (html
                                   [:a {:key  (str (:budget/uuid budget-props))
                                          ;:on-click #(om/transact! this `[(dashboard/set-active-budget ~{:budget-uuid (:budget/uuid budget-props)})])
                                        :href (routes/inside "/dashboard/" (:budget/uuid budget-props))}
                                    [:div
                                     (opts {:style {:border "1px solid #e7e7e7"
                                                    :height "100%"
                                                    :width  "100%"}})
                                     [:h3 (:budget/name budget-props)]
                                     [:table.table.table-striped.table-hover
                                      [:thead
                                       [:tr
                                        [:th "Transactions"]
                                        [:th (count (:transaction/_budget budget-props))]]
                                       [:tr
                                        [:th
                                         (opts {:style {:vertical-align :top}})
                                         "Top tags"]
                                        [:th
                                         (let [transactions (:transaction/_budget budget-props)
                                               tags (reduce #(concat %1 (:transaction/tags %2)) [] transactions)
                                               sorted (sort-by count (group-by :tag/name tags))]
                                           (prn "Tags: " sorted)
                                           (take 3 (map (fn [tag]
                                                          [:div (first tag) " #" (count (second tag))])
                                                        sorted)))]]]]]]))
                               all-budgets))))
         ]))))

(def ->Profile (om/factory Profile))
