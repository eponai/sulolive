(ns eponai.mobile.ios.ui.projects
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.client.ui.color :as color]
    [eponai.client.lib.transactions :as lib.t]
    [eponai.client.utils :as client.utils]
    [eponai.web.ui.utils :as web.utils]
    [eponai.mobile.components :as c :refer [navigator-ios view text scroll-view list-view list-view-data-source segmented-control-ios]]
    [eponai.mobile.components.nav :as nav]
    [eponai.mobile.ios.ui.transaction-list :as t]
    [eponai.mobile.components.table-view :as tv]
    [eponai.mobile.ios.ui.dashboard :as d]
    [eponai.mobile.components.button :as button]
    [eponai.mobile.ios.ui.utils :as utils]
    [om.next :as om :refer-macros [defui]]
    [taoensso.timbre :refer-macros [debug]]))

(defn wrap-props [x]
  {:propsWrapper (constantly x)})

(defn unwrap-props [x]
  (if (map? x)
    x
    ((.-propsWrapper x))))

(defui ProjectMenu
  Object
  (render [this]
    (let [{:keys [on-change]} (om/get-computed this)
          {:keys [selected-item]} (om/props this)
          items [:dashboard :list]]
      (view
        nil
        (segmented-control-ios
          (opts
            {:values        ["Dashboard" "List"]
             :style         {:flex 1}
             :selectedIndex (utils/position #{selected-item} items)
             :onChange      #(when on-change
                              (let [selected-index (.. % -nativeEvent -selectedSegmentIndex)]
                                (on-change (get items selected-index))))}))))))

(def ->ProjectMenu (om/factory ProjectMenu {:keyfn #(str ProjectMenu)}))

(defui ProjectView
  Object
  (initLocalState [_]
    {:selected-item :list})
  (render [this]
    (let [{:keys [project transactions]} (unwrap-props (om/props this))
          {:keys [selected-item]} (om/get-state this)]
      (view (opts {:style {:flex 1}})
            (->ProjectMenu (om/computed
                             {:selected-item selected-item}
                             {:on-change #(om/update-state! this assoc :selected-item %)}))
            (cond (= selected-item :list)
                  (t/->TransactionList transactions)
                  (= selected-item :dashboard)
                  (d/->Dashboard))))))

(def ->ProjectView (om/factory ProjectView))

(defui ProjectWidget
  Object
  (render [this]
    (let [{:keys [project transactions]} (unwrap-props (om/props this))
          {:keys [on-select]} (om/get-computed this)]
      (button/custom
        {:key             [(:project/uuid project)]
         :on-press        on-select
         :highlight-color color/lightgray
         :style           {:margin 5}}
        (view
          (opts {:style {:borderWidth 1
                         :borderColor color/primary-blue
                         :padding 5
                         :borderRadius 3
                         :height      150
                         :backgroundColor "transparent"}})
          (text (opts {:style {:fontSize   24
                               :fontWeight "bold"
                               :color color/primary-blue}})
                (str (:project/name project)))
          (text nil
                (str "Transactions: " (count transactions))))))))

(def ->ProjectWidget (om/factory ProjectWidget))

(defui Main
  Object
  (render [this]
    (let [props (om/props this)
          nav (.-navigator props)
          {:keys [all-projects transactions-by-project]} (unwrap-props props)]
      (tv/->TableView
        (om/computed
          {:rows all-projects}
          {:render-row (fn [project]
                         (let [props {:project      project
                                      :transactions (get transactions-by-project (:project/uuid project))}]
                           (->ProjectWidget
                             (om/computed
                               props
                               {:on-select (fn []
                                             (.push nav #js {:title     (:project/name project)
                                                             :component ->ProjectView
                                                             :passProps (wrap-props props)}))}))))})))))

(def ->Main (om/factory Main {:keyfn #(str Main)}))

(defui Projects
  static om/IQuery
  (query [this]
    [{:query/transactions lib.t/full-transaction-pull-pattern}
     {:query/all-projects [:project/uuid :project/name :project/created-at]}])
  web.utils/ISyncStateWithProps
  (props->init-state [_ props]
    {:transactions-by-project
     (group-by (comp :project/uuid :transaction/project) (:query/transactions props))})

  Object
  (initLocalState [this]
    (web.utils/props->init-state this (om/props this)))
  (componentWillReceiveProps [this next-props]
    (web.utils/sync-with-received-props this next-props))
  (render [this]
    (let [{:keys [query/all-projects]} (om/props this)
          {:keys [transactions-by-project]} (om/get-state this)]
      (nav/navigator
        {:initial-route {:title     "Overview"
                         :component ->Main
                         :passProps (wrap-props {:all-projects            all-projects
                                                 :transactions-by-project transactions-by-project})}}))))

(def ->Projects (om/factory Projects))
