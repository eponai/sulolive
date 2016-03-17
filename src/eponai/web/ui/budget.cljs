(ns eponai.web.ui.budget
  (:require [eponai.web.ui.all-transactions :refer [->AllTransactions AllTransactions]]
            [eponai.web.ui.dashboard :refer [->Dashboard Dashboard]]
            [eponai.web.ui.navigation :as nav]
            [eponai.web.ui.utils :as utils]
            [eponai.client.ui :refer-macros [opts]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]))

(defn update-content [component content]
  (om/update-state! component assoc :content content))

(defn- submenu [component]
  (html
    [:ul.menu
     [:li
      [:a
       {:on-click #(update-content component :dashboard)}
       [:span "Dashboard"]]]
     [:li
      [:a
       {:on-click #(update-content component :transactions)}
       [:span "Transactions"]]]]))

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
         (nav/->NavbarSubmenu (om/computed {}
                                           {:content (submenu this)}))
         [:div
          (opts {:style {:padding-top "50px"}})
          (cond (= content :dashboard)
                (->Dashboard dashboard)

                (= content :transactions)
                (->AllTransactions all-transactions))]]))))

(def ->Budget (om/factory Budget))
