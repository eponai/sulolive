(ns eponai.mobile.ios.ui.projects
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.client.ui.color :as color]
    [eponai.mobile.components :refer [navigator-ios view text scroll-view list-view list-view-data-source segmented-control-ios]]
    [eponai.mobile.components.nav :as nav]
    [eponai.mobile.ios.ui.transaction-list :as t]
    [eponai.mobile.components.table-view :as tv]
    [eponai.mobile.ios.ui.dashboard :as d]
    [eponai.mobile.components.button :as button]
    [eponai.mobile.ios.ui.utils :as utils]
    [om.next :as om :refer-macros [defui]]
    [taoensso.timbre :refer-macros [debug]]))

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
    {:selected-item :dashboard})
  (render [this]
    (let [project (om/props this)
          transactions (aget project "_project")
          {:keys [selected-item]} (om/get-state this)]
      (view (opts {:style {:flex 1}})
            (->ProjectMenu (om/computed
                             {:selected-item selected-item}
                             {:on-change #(om/update-state! this assoc :selected-item %)}))
            (cond (= selected-item :list)
                  (t/->TransactionList transactions)
                  (= selected-item :dashboard)
                  (d/->Dashboard))))))

(def ->ProjectView (om/factory ProjectView {:keyfn #(str ProjectView)}))

(defui ProjectWidget
  Object
  (render [this]
    (let [{:keys [project]} (om/props this)
          {:keys [on-select]} (om/get-computed this)]
      (button/custom
        {:key [(.-uuid project)]
         :on-press on-select
         :highlight-color color/lightgray
         :style {:margin 5}}
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
                (str (.-name project)))
          (text nil
                (str "Transactions: " (count (aget project "_project")))))))))

(def ->ProjectWidget (om/factory ProjectWidget))

(defui Main
  Object
  (render [this]
    (let [nav (.-navigator (om/props this))
          all-projects (.-projects (om/props this))]
      (tv/->TableView
        (om/computed
          {:rows all-projects}
          {:render-row (fn [r]
                         (->ProjectWidget (om/computed
                                            {:project r}
                                            {:on-select (fn []
                                                          (.push nav #js {:title     (.-name r)
                                                                          :component ->ProjectView
                                                                          :passProps r}))})))})))))

(def ->Main (om/factory Main {:keyfn #(str Main)}))

(defui Projects
  static om/IQuery
  (query [this]
    [{:query/transactions [:transaction/uuid]}
     {:query/all-projects [:project/uuid
                           :project/name
                           {:transaction/_project [:transaction/title
                                                   :transaction/uuid
                                                   :transaction/amount
                                                   {:transaction/date [:date/ymd :date/timestamp]}
                                                   {:transaction/tags [:tag/name]}
                                                   {:transaction/currency [:currency/code]}
                                                   {:transaction/project [:project/uuid]}
                                                   {:transaction/type [:db/ident]}]}]}])
  Object
  (render [this]
    (let [{:keys [query/all-projects]} (om/props this)]
      (nav/navigator
        {:initial-route {:title     "Overview"
                         :component ->Main
                         :passProps {:projects all-projects}}}))))

(def ->Projects (om/factory Projects))
