(ns eponai.client.ui.dashboard
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [eponai.client.ui.add-widget :refer [NewWidget ->NewWidget]]
            [eponai.client.ui.widget :refer [Widget ->Widget]]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [error]]
            [eponai.client.ui.utils :as utils]
            [datascript.core :as d]))

(defn save-widget [component widget]
  (om/transact! component `[(widget/save ~(assoc widget :mutation-uuid (d/squuid)))
                            :query/dashboard]))

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
                        {:widget/_dashboard (om/get-query Widget)}
                        {:dashboard/budget [:budget/uuid
                                            :budget/name]}]}
     {:proxy/new-widget (om/get-query NewWidget)}])
  Object
  (initLocalState [_]
    {:edit? true})

  (update-grid-layout [this]
    (let [WidthProvider (.-WidthProvider (.-ReactGridLayout js/window))
          grid-element (WidthProvider (.-Responsive (.-ReactGridLayout js/window)))]
      (om/update-state! this assoc :grid-element grid-element)))

  (componentWillReceiveProps [this new-props]
    (let [widgets (:widget/_dashboard (:query/dashboard new-props))]
      (om/update-state! this assoc :layout (clj->js (generate-layout widgets)))))

  (componentDidMount [this]
    (let [widgets (:widget/_dashboard (:query/dashboard (om/props this)))
          sidebar (.getElementById js/document "sidebar-wrapper")]
      (.addEventListener sidebar "transitionend" #(.update-grid-layout this))
      (.update-grid-layout this)
      (om/update-state! this
                        assoc
                        :layout (clj->js (generate-layout widgets))
                        :content :dashboard)))

  (delete-widget [this widget]
    (om/transact! this `[(widget/delete ~(select-keys widget [:widget/uuid]))
                         :query/dashboard]))

  (render [this]
    (let [{:keys [query/dashboard
                  proxy/new-widget]} (om/props this)
          {:keys [layout
                  edit?
                  grid-element
                  edit-widget
                  add-widget?]} (om/get-state this)
          widgets (:widget/_dashboard dashboard)
          React (.-React js/window)]
      (html
        [:div
         [:button.btn.btn-default.btn-md
          {:on-click #(om/update-state! this update :edit? not)}
          [:i.fa.fa-pencil]]
         [:button.btn.btn-default.btn-md
          {:on-click #(om/update-state! this assoc :add-widget? true)}
          [:i.fa.fa-bar-chart]]

         (when add-widget?
           (utils/modal {:content  (->NewWidget (om/computed new-widget
                                                             {:on-close #(om/update-state! this assoc :add-widget? false)
                                                              :on-save  #(do
                                                                          (save-widget this %)
                                                                          (om/update-state! this assoc :add-widget? false))
                                                              :dashboard dashboard}))
                         :on-close #(om/update-state! this assoc :add-widget? false)
                         :class    "modal-lg"}))
         [:div (str "Edit: " edit?)]
         (when edit?
           [:style (css [:.react-grid-item
                         {:-webkit-box-shadow "0 1px 1px rgba(0, 0, 0, .5)" ;
                          :box-shadow         "0 1px 1px rgba(0, 0, 0, .5)"}
                         [:&:hover {:cursor             :move
                                    :-webkit-box-shadow "0 3px 9px rgba(0, 0, 0, .5)"
                                    :box-shadow         "0 3px 9px rgba(0, 0, 0, .5)"}]])])

         (when edit-widget
           (utils/modal {:content  (->NewWidget (om/computed new-widget
                                                             {:on-close #(om/update-state! this assoc :edit-widget nil)
                                                              :on-save  #(do
                                                                          (save-widget this %)
                                                                          (om/update-state! this assoc :edit-widget nil))
                                                              :dashboard   dashboard
                                                              :widget edit-widget}))
                         :on-close #(om/update-state! this assoc :edit-widget nil)
                         :class    "modal-lg"}))
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
                               (fn [widget-props]
                                 (.createElement React
                                                 "div"
                                                 #js {:key (str (:widget/uuid widget-props))}
                                                 (->Widget
                                                   (om/computed widget-props
                                                                {:on-delete (when edit? #(.delete-widget this %))
                                                                 :on-edit   (when edit? #(om/update-state! this assoc :edit-widget %))}))))
                               widgets))))]))))

(def ->Dashboard (om/factory Dashboard))
