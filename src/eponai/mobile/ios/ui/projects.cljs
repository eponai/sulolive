(ns eponai.mobile.ios.ui.projects
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [navigator-ios view text scroll-view list-view list-view-data-source]]
    [eponai.mobile.components.nav :as nav]
    [eponai.mobile.ios.ui.transactions :as transactions]
    [eponai.mobile.components.button :as button]
    [eponai.mobile.ios.ui.utils :as utils]
    [om.next :as om :refer-macros [defui]]
    [taoensso.timbre :refer-macros [debug]]))

(defui TransactionList
  Object
  (initLocalState [this]
    (let [transactions (om/props this)
          data-source (js/ReactNative.ListView.DataSource. #js {:rowHasChanged (fn [prev next]
                                                                                 (debug "Row has changed: " prev)
                                                                                 (debug "Next " next)
                                                                                 (not= prev next))})]
      (debug "Created data-source: " data-source)
      (debug "Should use tarnsactions: " transactions)
      {:data-source (if (seq transactions)
                      (.. data-source (cloneWithRows transactions))
                      data-source)}))
  ;(componentWillReceiveProps [this new-props]
  ;  (let [{:keys [data-source]} (om/get-state this)]
  ;    (.. data-source
  ;        (cloneWithRows new-props))))
  (render [this]
    (let [transactions (om/props this)
          {:keys [data-source]} (om/get-state this)]
      (debug "Transaction list: " transactions)
      (debug "Got datasource: " data-source)
      (text nil "Transaction list on the way")
      (list-view
        (opts {:dataSource data-source
               :renderRow  (fn [row-data]
                             (debug "Row data: " row-data)
                             (button/custom
                               {:key [(.-title row-data)]
                                :on-press (fn [] (debug "Pressed item: " row-data))}
                               (view (opts {:style {:flexDirection "row"
                                                    :justifyContent "space-between"}})
                                     (text nil (.. row-data -date -ymd))
                                     (text nil (.-title row-data))
                                     (text nil (.-amount row-data))
                                     (text nil (.. row-data -currency -code)))))}))
      )))

(def ->TransactionList (om/factory TransactionList))

(defui ProjectView
  Object
  (render [this]
    (let [project (om/props this)
          transactions (aget project "_project")]
      (debug "Getting project: " transactions)
      (view (opts {:style {:flex 1}})
            (text (opts {:style {:fontSize  24
                                 :textAlign "center"}})
                  (.-name project))
            (->TransactionList transactions)))))

(def ->ProjectView (om/factory ProjectView))

(defui Main
  Object
  (render [this]
    (let [w (:width utils/screen-size)
          all-projects (.-projects (om/props this))]

      (scroll-view
        (opts {:horizontal     true
               :pagingEnabled  true
               :showsHorizontalScrollIndicator false})
        (map (fn [p]
               (view (opts {:style {:flex            1
                                    :margin          10
                                    :width           (- w 20)}
                            :key [(.-name p)]})
                     (->ProjectView p)))
             all-projects)))))

(def ->Main (om/factory Main))

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
      (nav/clean-navigator
        {:initial-route {:title     ""
                         :component ->Main
                         :passProps {:projects all-projects}}}))))

(def ->Projects (om/factory Projects))
