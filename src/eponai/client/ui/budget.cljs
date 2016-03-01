(ns eponai.client.ui.budget
  (:require [eponai.client.ui.all_transactions :refer [->AllTransactions AllTransactions]]
            [eponai.client.ui.dashboard :refer [->Dashboard Dashboard]]
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
    {:content :transactions})
  (componentWillUnmount [this]
    (om/transact! this `[(ui.component.budget/clear)
                         :query/all-transactions]))
  (render [this]
    (let [{:keys [proxy/dashboard
                  proxy/all-transactions]} (om/props this)
          {:keys [content]} (om/get-state this)]
      (html
        [:div
         [:h4]
         [:div.text-center
          [:button.btn.btn-default.btn-md
           {:on-click #(update-content this :dashboard)}
           [:span "Dashboard"]]
          [:button.btn.btn-default.btn-md
           {:on-click #(update-content this :transactions)}
           [:span "Transactions"]]]
         (cond (= content :dashboard)
               (->Dashboard dashboard)

               (= content :transactions)
               (->AllTransactions all-transactions))]))))

(def ->Budget (om/factory Budget))
