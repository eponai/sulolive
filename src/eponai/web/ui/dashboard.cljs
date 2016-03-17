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

(defmulti grid-layout (fn [k num-cols _]
                        (if (< 1 num-cols)
                          :multi-col
                          :single-col)))

(defmethod grid-layout :multi-col
  [k num-cols widgets]
  (let [layout-fn (fn [{:keys [widget/index] :as widget}]
                    {:x (mod index num-cols)
                     :y (int (/ index num-cols))
                     :w (max 1 (.floor js/Math (/ (* (:widget/width widget) num-cols) 100)))
                     :h (:widget/height widget)
                     :i (str (:widget/uuid widget))})]
    (map layout-fn widgets)))

(defmethod grid-layout :single-col
  [k num-cols widgets]
  (let [layout-fn (fn [{:keys [widget/index] :as widget}]
                    {:x 0
                     :y index
                     :w 1
                     :h (:widget/height widget)
                     :i (str (:widget/uuid widget))})]
    (map layout-fn widgets)))

(defn widgets->layout [cols widgets]
  (reduce (fn [m [k v]]
            (assoc m k (grid-layout k v widgets)))
          {}
          cols))

(defn layout->widgets [cols widgets layout]
  (let [num-cols (:lg cols)
        grouped (group-by :widget/uuid widgets)]
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

(defn calculate-last-index [cols widgets]
  (let [num-cols (:lg cols)
        {:keys [widget/index
                widget/height
                widget/width]} (last (sort-by :widget/index widgets))]
    (max 0 (+ index (* num-cols (dec height)) (.round js/Math (/ (* width num-cols) 100))))))

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
    (let [{:keys [cols]} (om/get-state this)
          widgets (:widget/_dashboard (:query/dashboard new-props))]
      (om/update-state! this assoc :layout (clj->js (widgets->layout cols widgets)))))

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
  (componentWillUnmount [_]
    (let [sidebar (.getElementById js/document "sidebar")]
      (when sidebar
        (.removeEventListener sidebar "transitionend"))))

  (layout-changed [this widgets layout]
    (let [{:keys [cols]} (om/get-state this)
          new-layout (layout->widgets cols widgets (js->clj layout))]
      (om/transact! this `[(dashboard/save ~{:widget-layout new-layout
                                             :mutation-uuid (d/squuid)})
                           :query/dashboard])))

  (delete-widget [this widget]
    (om/transact! this `[(widget/delete ~(assoc (select-keys widget [:widget/uuid]) :mutation-uuid (d/squuid)))
                         :query/dashboard]))

  (edit-start [this]
    (om/update-state! this assoc :is-editing? true))
  (edit-stop [this widgets layout]
    (let [{:keys [cols]} (om/get-state this)
          new-layout (layout->widgets cols widgets (js->clj layout))]
      (om/transact! this `[(dashboard/save ~{:widget-layout new-layout
                                             :mutation-uuid (d/squuid)})
                           :query/dashboard])
      (om/update-state! this assoc :is-editing? false)))

  (initLocalState [_]
    {:cols {:lg 4 :md 3 :sm 2 :xs 1 :xxs 1}})
  (render [this]
    (let [{:keys [query/dashboard
                  proxy/new-widget]} (om/props this)
          {:keys [layout
                  edit?
                  grid-element
                  edit-widget
                  add-widget?
                  is-editing?
                  cols]} (om/get-state this)
          widgets (:widget/_dashboard dashboard)
          React (.-React js/window)]
      (html
        [:div
         [:a.button.secondary
          (opts {:on-click #(om/update-state! this assoc :add-widget? true)
                 :style {:margin "0.5em"}})
          "Add widget"]

         (when (and layout
                    grid-element)
           (.createElement React
                           grid-element
                           #js {:className        (if is-editing? "layout animate" "layout"),
                                :draggableHandle  ".draggable"
                                :layouts          layout
                                :rowHeight        100,
                                :cols             (clj->js cols)
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
                                                              :index (calculate-last-index cols widgets)}))
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
