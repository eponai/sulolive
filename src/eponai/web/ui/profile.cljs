(ns eponai.web.ui.profile
  (:require [eponai.client.ui :refer-macros [opts]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [cljsjs.react-grid-layout]
            [eponai.web.routes :as routes]
            [garden.core :refer [css]]))

(defn generate-layout [projects]
  (map (fn [project i]
         {:x (mod i 4)
          :y (int (/ i 4))
          :w 1
          :h 1
          :i (str (:project/uuid project))})
       projects (range)))

(defui Profile
  static om/IQuery
  (query [_]
    [{:query/current-user [:user/uuid
                           :user/email]}
     {:query/all-projects [:project/uuid
                          :project/name
                          {:transaction/_project [:transaction/uuid
                                                 {:transaction/tags [:tag/name]}]}]}])
  Object
  (update-grid-layout [this]
    (let [WidthProvider (.-WidthProvider (.-ReactGridLayout js/window))
          grid-element (WidthProvider (.-Responsive (.-ReactGridLayout js/window)))]
      (om/update-state! this assoc :grid-element grid-element)))

  (componentWillReceiveProps [this new-props]
    (let [projects (:query/all-projects new-props)]
      (om/update-state! this assoc :layout (clj->js (generate-layout projects)))))

  (componentDidMount [this]
    (let [projects (:query/all-projects (om/props this))]
      (.update-grid-layout this)
      (om/update-state! this
                        assoc
                        :layout (clj->js (generate-layout projects)))))

  (render [this]
    (let [{:keys [query/current-user
                  query/all-projects]} (om/props this)
          {:keys [layout grid-element]} (om/get-state this)
          React (.-React js/window)]
      (html
        [:div

         [:style (css [:.react-grid-item
                       {:border "1px solid #e7e7e7"}
                       [:&:hover {:cursor             :pointer}]])]
         [:div
          (opts {:style {:display        :flex
                         :flex-direction :row
                         :align-items    :center}})
          [:h1 (:user/email current-user)]]

         [:a.button.secondary
          {:href (routes/inside "/transactions")}
          "All transactions"]

         (when layout
           (.createElement React
                           grid-element
                           #js {:className        "layout",
                                :layouts          #js {:lg layout :md layout}
                                :rowHeight        300,
                                :cols             #js {:lg 4 :md 4 :sm 2 :xs 1 :xxs 1}
                                :useCSSTransforms true
                                :isDraggable      false
                                :isResizable      false
                                :onResizeStop     #(.forceUpdate this)}

                           (clj->js
                             (map
                               (fn [project-props]
                                 (html
                                   [:div {:key  (str (:project/uuid project-props))}
                                    [:a
                                     (opts {:href  (routes/inside "/dashboard/" (:project/uuid project-props))
                                            :style {:border "1px solid #e7e7e7"
                                                    :height "100%"
                                                    :width  "100%"}})
                                     [:h3.text-center (:project/name project-props)]
                                     [:table
                                      (opts {:style {:width "100%"}})
                                      [:tbody
                                       [:tr
                                        [:td "Transactions"]
                                        [:td (count (:transaction/_project project-props))]]
                                       [:tr
                                        [:td
                                         (opts {:style {:vertical-align :top}})
                                         "Top tags"]
                                        [:td
                                         (let [transactions (:transaction/_project project-props)
                                               tags (reduce #(concat %1 (:transaction/tags %2)) [] transactions)
                                               sorted (sort-by count (group-by :tag/name tags))]
                                           (take 3 (map (fn [tag]
                                                          [:div
                                                           (opts {:key [(first tag)]})
                                                           (first tag) " #" (count (second tag))])
                                                        sorted)))]]]]]]))
                               all-projects))))]))))

(def ->Profile (om/factory Profile))
