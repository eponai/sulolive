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
             :style         {:flex 1
                             :marginVertical 10}
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
            (text (opts {:style {:fontSize  24
                                 :textAlign "center"
                                 :padding 10}})
                  (.-name project))
            (->ProjectMenu (om/computed
                             {:selected-item selected-item}
                             {:on-change #(om/update-state! this assoc :selected-item %)}))
            (cond (= selected-item :list)
                  (t/->TransactionList transactions)
                  (= selected-item :dashboard)
                  (d/->Dashboard))))))

(def ->ProjectView (om/factory ProjectView {:keyfn #(str ProjectView)}))

(defui Main
  Object
  (render [this]
    (let [w (:width utils/screen-size)
          all-projects (.-projects (om/props this))]

      (scroll-view
        (opts {:horizontal                     true
               :pagingEnabled                  true
               :showsHorizontalScrollIndicator false
               ;:style                          {:backgroundColor "yellow"}
               :contentContainerStyle          {:flex 1}
               :automaticallyAdjustContentInsets false})
        (map (fn [p]
               (view (opts {:style {:flexDirection "column"
                                    :flex            1
                                    :margin          10
                                    ;:padding 10
                                    :width           (- w 20)
                                    ;:backgroundColor "orange"
                                    }
                            :key [(.-name p)]})
                     (->ProjectView p)))
             all-projects)))))

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
      (nav/clean-navigator
        {:initial-route {:title     ""
                         :component ->Main
                         :passProps {:projects all-projects}}}))))

(def ->Projects (om/factory Projects))
