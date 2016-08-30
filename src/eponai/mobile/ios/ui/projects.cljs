(ns eponai.mobile.ios.ui.projects
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [navigator-ios view text scroll-view list-view list-view-data-source segmented-control-ios]]
    [eponai.mobile.components.nav :as nav]
    [eponai.mobile.ios.ui.transaction-list :as t]
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
          items [:list :dashboard]]
      (view
        nil
        (segmented-control-ios
          (opts
            {:values        ["List" "Dashboard"]
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
    (let [project (om/props this)
          transactions (aget project "_project")
          {:keys [selected-item]} (om/get-state this)]
      (view (opts {:style {:flex 1}})
            ;(view (opts {:flexDirection "row"
            ;             :justifyContent "space-between"})
            ;      (view nil)
            ;      (text (opts {:style {:fontSize  24
            ;                           :textAlign "center"}})
            ;            (.-name project))
            ;      (button/primary-hollow {:title "New"}))
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
      (debug "Got some project: " project)
      (button/custom
        {:key [(.-uuid project)]
         :on-press on-select}
        (view
          (opts {:style {:borderWidth 1
                         :height      150
                         :backgroundColor "transparent"}})
          (text (opts {:style {:fontSize   24
                               :fontWeight "bold"}})
                (str (.-name project)))
          (text nil
                (str "Transactions: " (count (aget project "_project")))))))))

(def ->ProjectWidget (om/factory ProjectWidget))

(defui Main
  Object
  (initLocalState [this]
    (let [all-projects (.-projects (om/props this))
          ds (js/ReactNative.ListView.DataSource. #js {:rowHasChanged (fn [prev next]
                                                                        (debug "Row has changed: " prev)
                                                                        (debug "Next " next)
                                                                        (not= prev next))})]
      {:data-source (if (seq all-projects)
                      (.cloneWithRows ds all-projects)
                      ds)}))
  (render [this]
    (let [nav (.-navigator (om/props this))
          {:keys [data-source]} (om/get-state this)]

      (list-view
        ;(opts {:horizontal                     true
        ;       :pagingEnabled                  true
        ;       :showsHorizontalScrollIndicator false
        ;       ;:style                          {:backgroundColor "yellow"}
        ;       :contentContainerStyle          {:flex 1}
        ;       :automaticallyAdjustContentInsets false})
        (opts {:dataSource data-source
               :automaticallyAdjustContentInsets false
               :renderRow  (fn [r]
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
