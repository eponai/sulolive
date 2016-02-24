(ns eponai.client.ui.budget
  (:require [eponai.client.ui.all_transactions :refer [->AllTransactions AllTransactions]]
            [eponai.client.ui.dashboard :refer [->Dashboard Dashboard]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]))

(defui Budget
  static om/IQuery
  (query [_]
    [{:proxy/dashboard (om/get-query Dashboard)}
     {:proxy/all-transactions (om/get-query AllTransactions)}])
  Object
  (initLocalState [_]
    {:content :dashboard})
  (render [this]
    (let [{:keys [proxy/dashboard
                  proxy/all-transactions]} (om/props this)
          {:keys [content]} (om/get-state this)]
      (html
        [:div
         [:h4]
         [:div.text-center
          [:button.btn.btn-default.btn-md
           {:on-click #(om/update-state! this assoc :content :dashboard)}
           [:span "Dashboard"]]
          [:button.btn.btn-default.btn-md
           {:on-click #(om/update-state! this assoc :content :transactions)}
           [:span "Transactions"]]]
         (cond (= content :dashboard)
               (->Dashboard dashboard)

               (= content :transactions)
               (->AllTransactions all-transactions))]))))

(def ->Budget (om/factory Budget))
