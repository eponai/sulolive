(ns eponai.mobile.ios.ui.transaction-list
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [view text list-view]]
    [eponai.mobile.components.button :as button]
    [eponai.mobile.components.table-view :as tv]
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
  (render [this]
    (let [transactions (om/props this)]

      (tv/->TableView
        (om/computed {:rows transactions}
                     {:render-row (fn [row-data]
                                    (->TransactionListItem (om/computed {:transaction row-data}
                                                                        {:on-press (fn [] (debug "Pressed item: " row-data))})))})))))

(def ->TransactionList (om/factory TransactionList {:keyfn #(str TransactionList)}))
