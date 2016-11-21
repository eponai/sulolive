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
        {:key [(:transaction/title transaction)]
         :on-press on-press}
        (view
          (opts {:style {:flexDirection "row"
                         :justifyContent "space-between"
                         :alignItems "center"
                         :padding 5
                         :height 34}})
              (text nil (get-in transaction [:transaction/date :date/ymd]))
              (text nil (:transaction/title transaction))
              (text nil (:transaction/amount transaction))
              (text nil (get-in transaction [:transaction/currency :currency/code])))))))

(def ->TransactionListItem (om/factory TransactionListItem))

(defui TransactionList
  Object
  (render [this]
    (let [transactions (om/props this)]

      (tv/->TableView
        (om/computed {:rows transactions}
                     {:render-row (fn [transaction]
                                    (->TransactionListItem (om/computed {:transaction transaction}
                                                                        {:on-press
                                                                         (fn [] (debug "Pressed item: " transaction))})))})))))

(def ->TransactionList (om/factory TransactionList))
