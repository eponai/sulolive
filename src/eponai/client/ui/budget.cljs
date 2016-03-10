(ns eponai.client.ui.budget
  (:require [eponai.client.ui.all_transactions :refer [->AllTransactions AllTransactions]]
            [eponai.client.ui.dashboard :refer [->Dashboard Dashboard]]
            [eponai.client.ui.utils :as utils]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]))

(defn update-content [component content]
  (om/update-state! component assoc :content content))

(defui Budget
  static om/IQueryParams
  (params [_]
    (let [component AllTransactions
          ;; TODO: HACK: Remove once we've upgrade to om.next alpha-31+
          query (om/get-query (or (when-let [r @utils/reconciler-atom]
                                    (om/class->any r component))
                                  component))]
      {:transactions query}))
  static om/IQuery
  (query [_]
    [{:proxy/dashboard (om/get-query Dashboard)}
     {:proxy/all-transactions '?transactions}])
  Object
  (initLocalState [_]
    {:content :transactions})
  (componentWillUnmount [this]
    (om/transact! this `[(ui.component.budget/clear)
                         :query/transactions]))
  (render [this]
    (let [{:keys [proxy/dashboard
                  proxy/all-transactions]} (om/props this)
          {:keys [content]} (om/get-state this)]
      (html
        [:div
         [:div.text-center
          [:a.button.secondary
           {:on-click #(update-content this :dashboard)}
           [:span "Dashboard"]]
          [:a.button.secondary
           {:on-click #(update-content this :transactions)}
           [:span "Transactions"]]]
         (cond (= content :dashboard)
               (->Dashboard dashboard)

               (= content :transactions)
               (->AllTransactions all-transactions))]))))

(def ->Budget (om/factory Budget))
