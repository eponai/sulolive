(ns eponai.web.ui.dashboard
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [eponai.web.ui.add-widget :refer [NewWidget ->NewWidget]]
            [eponai.web.ui.widget :refer [Widget ->Widget]]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [error debug]]
            [eponai.web.ui.utils :as utils]
            [datascript.core :as d]))

(defn save-widget [component widget]
  (om/transact! component `[(widget/save ~(assoc widget :mutation-uuid (d/squuid)))
                            :query/dashboard]))

(defn widgets->layout [widgets]
  (map (fn [{:keys [widget/index] :as widget}]
         {:x (mod index 3)
          :y (int (/ index 3))
          :w (.round js/Math (/ (* (:widget/width widget) 3) 100))
          :h (:widget/height widget)
          :i (str (:widget/uuid widget))})
       widgets))

(defn layout->widgets [widgets layout]
  (let [grouped (group-by :widget/uuid widgets)]
    (map (fn [item]
           (let [widget (first (get grouped (uuid (get item "i"))))
                 new-width (int (* 100 (/ (get item "w") 3)))
                 new-height (get item "h")
                 new-index (+ (* (get item "y") 3) (get item "x"))]
             {:widget/uuid (:widget/uuid widget)
              :widget/index  new-index
              :widget/width  new-width
              :widget/height new-height}))
         layout)))

(defn calculate-last-index [widgets]
  (let [{:keys [widget/index
                widget/height
                widget/width]} (last (sort-by :widget/index widgets))]
    (max 0 (+ index (* 3 (dec height)) (.round js/Math (/ (* width 3) 100))))))

(defn build-react [component props & children]
  (let [React (.-React js/window)]
    (.createElement React component (clj->js props) children)))

;;################ Om component #############

(defui Dashboard
  static om/IQuery
  (query [_]
    [{:query/dashboard [:dashboard/uuid
                        {:widget/_dashboard (om/get-query Widget)}
                        {:dashboard/budget [:budget/uuid
                                            :budget/name]}]}
     {:proxy/new-widget (om/get-query NewWidget)}])
  Object
  (componentWillReceiveProps [this new-props]
    (let [widgets (:widget/_dashboard (:query/dashboard new-props))]
      (om/update-state! this assoc :layout (clj->js (widgets->layout widgets)))))

  (componentDidMount [this]
    (let [widgets (:widget/_dashboard (:query/dashboard (om/props this)))
          WidthProvider (.-WidthProvider (.-ReactGridLayout js/window))
          grid-element (WidthProvider (.-Responsive (.-ReactGridLayout js/window)))]
      (om/update-state! this
                        assoc
                        :layout (clj->js (widgets->layout widgets))
                        :content :dashboard
                        :grid-element grid-element)))

  (layout-changed [this widgets layout]
    (let [new-layout (layout->widgets widgets (js->clj layout))]
      (om/transact! this `[(dashboard/save ~{:widget-layout new-layout
                                             :mutation-uuid (d/squuid)})
                           :query/dashboard])))

  (delete-widget [this widget]
    (om/transact! this `[(widget/delete ~(assoc (select-keys widget [:widget/uuid]) :mutation-uuid (d/squuid)))
                         :query/dashboard]))

  (edit-start [this]
    (om/update-state! this assoc :is-editing? true))
  (edit-stop [this widgets layout]
    (let [new-layout (layout->widgets widgets (js->clj layout))]
      (om/transact! this `[(dashboard/save ~{:widget-layout new-layout
                                             :mutation-uuid (d/squuid)})
                           :query/dashboard])
      (om/update-state! this assoc :is-editing? false)))

  (render [this]
    (let [{:keys [query/dashboard
                  proxy/new-widget]} (om/props this)
          {:keys [layout
                  edit?
                  grid-element
                  edit-widget
                  add-widget?
                  is-editing?]} (om/get-state this)
          widgets (:widget/_dashboard dashboard)
          React (.-React js/window)]
      (html
        [:div
         [:ul.menu.icon-top
          [:li
           [:a
            {:on-click #(om/update-state! this assoc :add-widget? true)}
            [:i.fa.fa-bar-chart]
            [:span "New"]]]]

         (when (and layout
                    grid-element)
           (.createElement React
                           grid-element
                           #js {:className        (if is-editing? "layout animate" "layout"),
                                :draggableHandle  ".draggable"
                                :layouts          #js {:lg layout :md layout}
                                :rowHeight        300,
                                :cols             #js {:lg 3 :md 2 :sm 2 :xs 1 :xxs 1}
                                :useCSSTransforms true
                                :isDraggable      true
                                :isResizable      true
                                :onResizeStart    #(.edit-start this)
                                :onResizeStop     #(.edit-stop this widgets %)
                                :onDragStart      #(.edit-start this)
                                :onDragStop       #(.edit-stop this widgets %)}
                           (clj->js
                             (map
                               (fn [widget-props]
                                 (.createElement React
                                                 "div"
                                                 #js {:key   (str (:widget/uuid widget-props))}
                                                      (->Widget
                                                        (om/computed widget-props
                                                                     {:on-edit #(om/update-state! this assoc :edit-widget %)}))))
                               (sort-by :widget/index widgets)))))

         (when add-widget?
           (utils/modal {:content  (->NewWidget (om/computed new-widget
                                                             {:on-close #(om/update-state! this assoc :add-widget? false)
                                                              :on-save  #(do
                                                                          (save-widget this %)
                                                                          (om/update-state! this assoc :add-widget? false))
                                                              :dashboard dashboard
                                                              :index (calculate-last-index widgets)}))
                         :on-close #(om/update-state! this assoc :add-widget? false)
                         :size "large"}))

         (when edit-widget
           (utils/modal {:content  (->NewWidget (om/computed new-widget
                                                             {:on-close  #(om/update-state! this assoc :edit-widget nil)
                                                              :on-save   #(do
                                                                           (save-widget this %)
                                                                           (om/update-state! this assoc :edit-widget nil))
                                                              :on-delete #(.delete-widget this %)
                                                              :dashboard dashboard
                                                              :widget    edit-widget}))
                         :on-close #(om/update-state! this assoc :edit-widget nil)
                         :size     "large"}))]))))

(def ->Dashboard (om/factory Dashboard))
