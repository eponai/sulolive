(ns eponai.web.ui.add-widget.select-graph
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.widget :as widget]
    [eponai.web.routes :as routes]
    [garden.core :refer [css]]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [eponai.common.report :as report]
    [taoensso.timbre :refer-macros [debug]]))

(def styles [:graph.style/bar
             :graph.style/area
             :graph.style/line
             :graph.style/number])

(defn default-report [style]
  (-> {:report/group-by (cond (= style :graph.style/bar)
                              :transaction/tags
                              (= style :graph.style/number)
                              :default
                              true
                              :transaction/date)}
      (assoc :report/functions [{:report.function/id :report.function.id/sum}])))

(defn generate-layout []
  (map (fn [style i]
         {:x (mod i 2)
          :y (int (/ i 2))
          :w 1
          :h 1
          :i (str style)})
       styles
       (range)))

(defui SelectGraph
  Object
  (update-grid-layout [this]
    (let [WidthProvider (.-WidthProvider (.-ReactGridLayout js/window))
          grid-element (WidthProvider (.-Responsive (.-ReactGridLayout js/window)))]
      (om/update-state! this assoc :grid-element grid-element)))

  (componentWillReceiveProps [this new-props]
    (let [{:keys [transactions]} (::om/computed new-props)
          style->data (reduce (fn [m style]
                                (assoc m style (report/generate-data (default-report style) nil transactions)))
                              {}
                              styles)]
      (om/update-state! this assoc :style->data style->data)))

  (componentDidMount [this]
    (let [{:keys [transactions]} (om/get-computed this)
          style->data (reduce (fn [m style]
                                (assoc m style (report/generate-data (default-report style) nil transactions)))
                              {}
                              styles)
          sidebar (.getElementById js/document "sidebar")]
      (when sidebar
        (.addEventListener sidebar "transitionend" #(.update-grid-layout this)))
      (.update-grid-layout this)
      (om/update-state! this
                        assoc
                        :layout (clj->js (generate-layout))
                        :style->data style->data)))
  (componentWillUnmount [this]
    (let [sidebar (.getElementById js/document "sidebar")]
      (when sidebar
        (.removeEventListener sidebar "transitionend" #(.update-grid-layout this)))))
  (render [this]
    (let [{:keys [graph]} (om/props this)
          {:keys [layout
                  grid-element
                  style->data]} (om/get-state this)
          {:keys [on-change
                  on-next]} (om/get-computed this)
          React (.-React js/window)]
      (html
        [:div

         [:style (css [:.react-grid-item
                       {:border "1px solid #e7e7e7"}
                       [:&:hover {:cursor             :pointer}]])]
         ;[:ul.breadcrumbs
         ; ;[:li
         ; ; [:a
         ; ;  ;{:href (if (:db/id (:dashboard/project dashboard))
         ; ;  ;         (routes/key->route :route/project->dashboard
         ; ;  ;                            {:route-param/project-id (:db/id (:dashboard/project dashboard))})
         ; ;  ;         "#")}
         ; ;  "Dashboard"]]
         ; [:li.disabled
         ;  [:span "New widget"]]
         ; [:li
         ;  [:span "Select Graph"]]]


         ;[:div.callout.clearfix]
         [:h4 "Select graph style"]
         (when layout
           (.createElement React
                           grid-element
                           #js {:className        "layout select-graph",
                                :layouts          #js {:lg layout :md layout}
                                :rowHeight        200,
                                :margin           #js [60 20]
                                :cols             #js {:lg 2 :md 2 :sm 2 :xs 1 :xxs 1}
                                :useCSSTransforms true
                                :isDraggable      false
                                :isResizable      false
                                :onResizeStop     #(.forceUpdate this)}

                           (into-array
                             (map
                               (fn [style]
                                 (html
                                   [:div
                                    {:key      (str style)
                                     :class    (when (= style (:graph/style graph))
                                                 "selected")
                                     :on-click #(when on-change
                                                 (on-change style))}
                                    (widget/->Widget (om/computed
                                                       {:widget/graph  {:graph/style style}
                                                        :widget/report {:report/title (clojure.string/capitalize (name style))}
                                                        :widget/data   (get style->data style)}
                                                       {:id (name style)}))]))
                               styles))))
         [:div.float-right
          [:a.button.secondary
           ;{:on-click on-close}
           "Cancel"]
          [:a.button.primary
           {:on-click #(when on-next
                        (on-next))}
           "Next"]]]))))

(def ->SelectGraph (om/factory SelectGraph))