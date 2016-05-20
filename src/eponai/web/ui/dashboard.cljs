(ns eponai.web.ui.dashboard
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
    [datascript.core :as d]
    [eponai.web.ui.all-transactions :refer [Transaction]]
    [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
    [eponai.web.routes :as routes]
    [eponai.web.ui.add-widget :refer [NewWidget ->NewWidget]]
    [eponai.web.ui.widget :refer [Widget ->Widget]]
    [garden.core :refer [css]]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [error debug]]
    [eponai.web.ui.daterangepicker :refer [->DateRangePicker]]))

(defn min-dimensions [widget num-cols]
  (let [style (get-in widget [:widget/graph :graph/style])
        calc-w (fn [min-w]
                      (.floor js/Math (/ (* min-w num-cols) 100)))]

    (cond (or
            (= style :graph.style/area)
            (= style :graph.style/line)
            (= style :graph.style/bar)
            (= style :graph.style/burndown))
          {:minW (calc-w 50)
           :minH 2
           :maxH 3}

          (= style :graph.style/chord)
          {:minW (calc-w 50)
           :minH 4}

          :else
          {:minW 1
           :maxH 2})))

(defn grid-layout
  [num-cols widgets]
  (let [layout-fn (fn [{:keys [widget/index] :as widget}]
                    (merge
                      {:x    (mod index num-cols)
                       :y    (int (/ index num-cols))
                       :w    (max 1 (.floor js/Math (/ (* (:widget/width widget) num-cols) 100)))
                       :h    (:widget/height widget)
                       :i    (str (:widget/uuid widget))}
                      (min-dimensions widget num-cols)))]
    (map layout-fn widgets)))

(defn widgets->layout [cols widgets]
  (reduce (fn [m [breakpoint num-cols]]
            (assoc m breakpoint (grid-layout num-cols widgets)))
          {}
          cols))

(defn layout->widgets [num-cols widgets layout]
  (let [grouped (group-by :widget/uuid widgets)]
    (map (fn [item]
           (let [widget (first (get grouped (uuid (get item "i"))))
                 new-width (int (* 100 (/ (get item "w") num-cols)))
                 new-height (get item "h")
                 new-index (+ (* (get item "y") num-cols) (get item "x"))]
             {:widget/uuid (:widget/uuid widget)
              :widget/index  new-index
              :widget/width  new-width
              :widget/height new-height}))
         layout)))

(defn calculate-last-index [widgets]
  (let [{:keys [widget/index]} (last (sort-by :widget/index widgets))]
    (if index
      (inc index)
      0)))

(defn build-react [component props & children]
  (let [React (.-React js/window)]
    (.createElement React component (clj->js props) children)))

;;################ Om component #############

(defui Dashboard
  static om/IQuery
  (query [_]
    [{:query/dashboard [:dashboard/uuid
                        {:widget/_dashboard (om/get-query Widget)}
                        {:dashboard/project [:db/id
                                             :project/uuid
                                             :project/name
                                             :project/users]}]}
     {:query/transactions (om/get-query Transaction)}])
  Object
  (componentWillReceiveProps [this new-props]
    (let [{:keys [cols]} (om/get-state this)
          widgets (:widget/_dashboard (:query/dashboard new-props))]
      (om/update-state! this assoc
                        :layout (clj->js (widgets->layout cols widgets)))))

  (update-layout [this]
    (let [{:keys [cols]} (om/get-state this)
          widgets (:widget/_dashboard (:query/dashboard (om/props this)))
          WidthProvider (.-WidthProvider (.-ReactGridLayout js/window))
          grid-element (WidthProvider (.-Responsive (.-ReactGridLayout js/window)))]
      (om/update-state! this
                        assoc
                        :layout (clj->js (widgets->layout cols widgets))
                        :content :dashboard
                        :grid-element grid-element)))
  (componentDidMount [this]
    (let [sidebar (.getElementById js/document "sidebar")]
      (when sidebar
        (.addEventListener sidebar "transitionend" #(.update-layout this)))
      (.update-layout this)))
  (componentWillUnmount [this]
    (let [sidebar (.getElementById js/document "sidebar")]
      (when sidebar
        (.removeEventListener sidebar "transitionend" #(.update-layout this)))))

  (save-layout [this widgets layout]
    (let [{:keys [cols breakpoint]} (om/get-state this)
          num-cols (get cols breakpoint)
          new-layout (layout->widgets num-cols widgets (js->clj layout))]
      (om/transact! this `[(dashboard/save ~{:widget-layout new-layout
                                             :mutation-uuid (d/squuid)})
                           :query/dashboard])))

  (layout-changed [this widgets layout]
    (.save-layout this widgets layout))

  (edit-start [this]
    (om/update-state! this assoc :is-editing? true))
  (edit-stop [this widgets layout]
    (.save-layout this widgets layout)
    (om/update-state! this assoc :is-editing? false))

  (on-breakpoint-change [this breakpoint]
    (om/update-state! this assoc :breakpoint (keyword breakpoint)))

  (initLocalState [_]
    {:cols {:lg 4 :md 4 :sm 2 :xs 1 :xxs 1}
     :breakpoint :lg})
  (render [this]
    (let [{:keys [query/dashboard
                  query/transactions]} (om/props this)
          {:keys [layout
                  grid-element
                  is-editing?
                  cols]} (om/get-state this)
          widgets (:widget/_dashboard dashboard)
          project-id (:db/id (:dashboard/project dashboard))
          React (.-React js/window)]
      (html
        [:div
         [:div#dashboard-menu.row.column.small-12.expanded
          [:div.menu-horizontal
           [:a.nav-link
            (opts {:href  (when project-id (routes/key->route :route/project->widget+type+id {:route-param/project-id  project-id
                                                                                              :route-param/widget-type :track
                                                                                              :route-param/widget-id   "new"}))
                   :style {:padding "0.5em"}})
            [:i.fa.fa-line-chart.fa-fw
             (opts {:style {:color "green"}})]
            [:span.small-caps "Track"]]
           [:a.nav-link
            (opts {:href  (when project-id
                            (routes/key->route :route/project->widget+type+id {:route-param/project-id  project-id
                                                                               :route-param/widget-type :goal
                                                                               :route-param/widget-id   "new"}))
                   :style {:padding "0.5em"}})
            [:i.fa.fa-star.fa-fw
             (opts {:style {:color "orange"}})]
            [:span.small-caps "Goal"]]]]

         (if (and layout
                  grid-element
                  (seq widgets))
           (.createElement React
                           grid-element
                           #js {:className          (if is-editing? "layout animate" "layout"),
                                :draggableHandle    ".widget-move"
                                :layouts            layout
                                :rowHeight          100
                                :margin             #js [20 20]
                                :cols               (clj->js cols)
                                :useCSSTransforms   true
                                :isDraggable        true
                                :isResizable        true
                                :onBreakpointChange #(.on-breakpoint-change this %)
                                :onResizeStart      #(.edit-start this)
                                :onResizeStop       #(.edit-stop this widgets %)
                                :onDragStart        #(.edit-start this)
                                :onDragStop         #(.edit-stop this widgets %)}
                           (into-array
                             (map
                               (fn [widget-props]
                                 (.createElement React
                                                 "div"
                                                 #js {:key (str (:widget/uuid widget-props))}
                                                 (->Widget
                                                   (om/computed widget-props
                                                                {:project-id project-id
                                                                 :transactions transactions}))))
                               (sort-by :widget/index widgets))))
           [:div.empty-message.text-center
            [:i.fa.fa-tachometer.fa-5x]
            [:div.lead
             "It's a slow day here, your dashboard is empty."
             [:br]
             [:br]
             "Get started with the action and "
             [:a.link
              {:on-click #(om/update-state! this assoc :add-widget? true)}
              "add a new widget"]
             "."]])]))))

(def ->Dashboard (om/factory Dashboard))
