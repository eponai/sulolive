(ns eponai.mobile.ios.ui.transaction-list
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [view text list-view]]
    [eponai.mobile.components.button :as button]
    [om.next :as om :refer-macros [defui]]
    [taoensso.timbre :refer-macros [debug]]))

(defui TransactionListItem
  Object
  (render [this]
    (let [{:keys [transaction]} (om/props this)
          {:keys [on-press]} (om/get-computed this)]
      (button/custom
        {:key [(.-title transaction)]
         :on-press on-press}
        (view (opts {:style {:flexDirection "row"
                             :justifyContent "space-between"}})
              (text nil (.. transaction -date -ymd))
              (text nil (.-title transaction))
              (text nil (.-amount transaction))
              (text nil (.. transaction -currency -code)))))))

(def ->TransactionListItem (om/factory TransactionListItem {:keyfn #(str TransactionListItem)}))

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
    (let [{:keys [data-source]} (om/get-state this)]
      (if (< 0 (.getRowCount data-source))
        (view (opts {:key   ["transaction-list"]
                     :style {:flex 1}})
              (list-view
                (opts {:dataSource                       data-source
                       :style                            {:flex       1
                                                          :paddingTop 10}
                       :automaticallyAdjustContentInsets false
                       :renderRow                        (fn [row-data]
                                                           (->TransactionListItem (om/computed {:transaction row-data}
                                                                                               {:on-press (fn [] (debug "Pressed item: " row-data))})))})))
        (text nil "No transactions in this project")))))

(def ->TransactionList (om/factory TransactionList {:keyfn #(str TransactionList)}))
