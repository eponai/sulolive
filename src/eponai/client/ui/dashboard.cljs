(ns eponai.client.ui.dashboard
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [eponai.client.report :as report]
            [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [eponai.client.ui.d3 :as d3]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [error]]))


(defui Widget
  static om/IQuery
  (query [_]
    [:widget/uuid
     :widget/width
     :widget/height
     :widget/filter
     {:widget/report [:report/uuid
                      :report/group-by
                      :report/title
                      {:report/functions [:report.function/uuid
                                          :report.function/attribute
                                          :report.function/id]}]}
     {:widget/graph [:graph/style]}])

  Object
  (render [this]
    (let [{:keys [widget/report
                  widget/graph] :as widget} (om/props this)
          {:keys [on-delete
                  data]} (om/get-computed this)
          report-data (report/create report data)]
      (html
        [:div.widget
         (opts {:style {:border        "1px solid #e7e7e7"
                        ;:border-radius "0.5em"
                        :padding       "30px 0 0 0"
                        :width         "100%"
                        :height        "100%"
                        :position      :relative
                        :box-sizing    :border-box
                        :background :white}})
         [:div
          (opts {:style {:position        :absolute
                         :top             0
                         :left            10
                         :right           10
                         :height          30
                         :display         :flex
                         :flex-direction  :row
                         :justify-content :space-between
                         :align-items     :flex-start}})
          [:h5
           (:report/title report)]
          (when on-delete
            [:a.close
             {:on-click #(on-delete widget)}
             "x"])]
         (let [{:keys [graph/style]} graph
               settings {:data         report-data
                         :id           (str (or (:widget/uuid widget) "widget"))
                         :width        "100%"
                         :height       "100%"
                         :title-axis-y "Amount ($)"}]
           (cond (= style :graph.style/bar)
                 (d3/->BarChart settings)

                 (= style :graph.style/area)
                 (d3/->AreaChart settings)

                 (= style :graph.style/number)
                 (d3/->NumberChart settings)))]))))

(def ->Widget (om/factory Widget))

(defn generate-layout [widgets]
  (map (fn [widget i]
         {:x (mod i 4)
          :y (int (/ i 4))
          :w 1
          :h 1
          :i (str (:widget/uuid widget))})
       widgets (range)))

(defn build-react [component props & children]
  (let [React (.-React js/window)]
    (.createElement React component (clj->js props) children)))

(defui Dashboard
  static om/IQuery
  (query [_]
    [{:query/dashboard [:dashboard/uuid
                        {:dashboard/widgets (om/get-query Widget)}
                        {:dashboard/budget [:budget/uuid
                                            {:transaction/_budget [:transaction/uuid
                                                                   {:transaction/tags [:tag/name]}
                                                                   :transaction/amount
                                                                   {:transaction/date [:date/ymd
                                                                                       :date/timestamp]}]}]}]}])
  Object
  (initLocalState [_]
    {:edit? true})

  (update-grid-layout [this]
    (let [WidthProvider (.-WidthProvider (.-ReactGridLayout js/window))
          grid-element (WidthProvider (.-Responsive (.-ReactGridLayout js/window)))]
      (om/update-state! this assoc :grid-element grid-element)))

  (componentWillReceiveProps [this new-props]
    (let [{:keys [layout]} (om/get-state this)
          widgets (:dashboard/widgets (:query/dashboard new-props))]
      (when-not (seq layout)
        (om/update-state! this assoc :layout (clj->js (generate-layout widgets))))))

  (componentDidMount [this]
    (let [widgets (:dashboard/widgets (:query/dashboard (om/props this)))
          sidebar (.getElementById js/document "sidebar-wrapper")]
      (.addEventListener sidebar "transitionend" #(.update-grid-layout this))
      (.update-grid-layout this)
      (om/update-state! this
                        assoc
                        :layout (clj->js (generate-layout widgets)))))

  (delete-widget [this widget]
    (om/transact! this `[(widget/delete ~(select-keys widget [:widget/uuid]))
                         :query/dashboard]))

  (render [this]
    (let [{:keys [query/dashboard]} (om/props this)
          {:keys [layout
                  edit?
                  grid-element]} (om/get-state this)
          widgets (:dashboard/widgets dashboard)
          React (.-React js/window)]
      (html
        [:div
         [:button.btn.btn-default.btn-md
          {:on-click #(om/update-state! this update :edit? not)}
          [:i.fa.fa-pencil]]
         [:div (str "Edit: " edit?)]
         (when edit?
           [:style (css [:.react-grid-item
                         {:-webkit-box-shadow "0 1px 1px rgba(0, 0, 0, .5)" ;
                          :box-shadow         "0 1px 1px rgba(0, 0, 0, .5)"}
                         [:&:hover {:cursor             :move
                                    :-webkit-box-shadow "0 3px 9px rgba(0, 0, 0, .5)"
                                    :box-shadow         "0 3px 9px rgba(0, 0, 0, .5)"}]])])
         (when (and layout
                    grid-element)
           (.createElement React
                           grid-element
                           #js {:className        "layout",
                                :layouts          #js {:lg layout :md layout}
                                :rowHeight        300,
                                :cols             #js {:lg 3 :md 2 :sm 2 :xs 1 :xxs 1}
                                :useCSSTransforms true
                                :isDraggable      edit?
                                :isResizable      edit?
                                :onResizeStop     #(.forceUpdate this)}

                           (clj->js
                             (map
                               (fn [widget-props i]
                                 (.createElement React
                                                 "div"
                                                 #js {:key (str (:widget/uuid widget-props))}
                                                 (->Widget
                                                   (om/computed widget-props
                                                                {:data      (-> dashboard
                                                                                :dashboard/budget
                                                                                :transaction/_budget)
                                                                 :on-delete #(.delete-widget this %)}))))
                               widgets
                               (range)))))])

      )))

(def ->Dashboard (om/factory Dashboard))
