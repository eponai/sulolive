(ns eponai.web.ui.budget
  (:require [eponai.web.ui.all-transactions :refer [->AllTransactions AllTransactions]]
            [eponai.web.ui.dashboard :refer [->Dashboard Dashboard]]
            [eponai.web.ui.utils :as utils]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]))

(defn update-content [component content]
  (om/update-state! component assoc :content content))

(defui Budget
  static om/IQuery
  (query [_]
    [{:proxy/dashboard (om/get-query Dashboard)}
     {:proxy/all-transactions (om/get-query AllTransactions)}])
  Object
  (initLocalState [_]
    {:content :dashboard})
  (componentWillUnmount [this]
    (om/transact! this `[(ui.component.budget/clear)
                         :query/transactions]))
  (render [this]
    (let [{:keys [proxy/dashboard
                  proxy/all-transactions]} (om/props this)
          {:keys [content]} (om/get-state this)]
      (html
        [:div
         [:ul.menu
          [:li
           [:a.button.secondary
            {:on-click #(update-content this :dashboard)}
            [:span "Dashboard"]]]
          [:li
           [:a.button.secondary
            {:on-click #(update-content this :transactions)}
            [:span "Transactions"]]]]
         (cond (= content :dashboard)
               (->Dashboard dashboard)

               (= content :transactions)
               (->AllTransactions all-transactions))]))))

(def ->Budget (om/factory Budget))
