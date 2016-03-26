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

(defn- submenu [component users]
  (html
    [:ul.menu
     [:li
      [:a.disabled
       [:i.fa.fa-user]
       [:small (count users)]]]
     [:li
      [:a
       {:on-click #(.share component)}
       [:i.fa.fa-share-alt]]]
     [:li
      [:a
       {:on-click #(update-content component :dashboard)}
       [:span "Dashboard"]]]
     [:li
      [:a
       {:on-click #(update-content component :transactions)}
       [:span "Transactions"]]]]))

(defui ShareBudget
  Object
  (render [this]
    (let [{:keys [on-close on-save]} (om/get-computed this)
          {:keys [input-email]} (om/get-state this)]
      (html
        [:div.clearfix
         [:h3 "Share"]
         [:label "Invite friend to share this budget:"]
         [:input
          {:type        "email"
           :value       input-email
           :on-change   #(om/update-state! this assoc :input-email (.. % -target -value))
           :placeholder "yourfriend@example.com"}]

         [:div.float-right
          [:a.button.secondary
           {:on-click on-close}
           "Cancel"]
          [:a.button.primary
           {:on-click #(do (on-save input-email)
                           (on-close))}
           "Share"]]]))))

(def ->ShareBudget (om/factory ShareBudget))

(defui Budget
  static om/IQuery
  (query [_]
    [{:proxy/dashboard (om/get-query Dashboard)}
     {:proxy/all-transactions (om/get-query AllTransactions)}])
  Object
  (share [this]
    (om/update-state! this assoc :share-budget? true))

  (share-budget [this budget-uuid email]
    (om/transact! this `[(budget/share ~{:budget/uuid budget-uuid
                                         :user/email email})]))
  (init-state [_]
    {:content :transactions})
  (initLocalState [this]
    (.init-state this))

  (componentWillUnmount [this]
    (om/transact! this `[(ui.component.budget/clear)
                         :query/transactions]))

  (render [this]
    (let [{:keys [proxy/dashboard
                  proxy/all-transactions]} (om/props this)
          {:keys [content
                  share-budget?]} (om/get-state this)
          budget (-> dashboard
                     :query/dashboard
                     :dashboard/budget)]
      (html
        [:div
         (nav/->NavbarSubmenu (om/computed {}
                                           {:content (submenu this (:budget/users budget))}))
         [:div#budget-content
          (cond (= content :dashboard)
                (->Dashboard dashboard)

                (= content :transactions)
                (->AllTransactions all-transactions))]

         (when share-budget?
           (let [on-close #(om/update-state! this assoc :share-budget? false)]
             (utils/modal {:content (->ShareBudget (om/computed {}
                                                                {:on-close on-close
                                                                 :on-save #(.share-budget this (:budget/uuid budget) %)}))
                           :on-close on-close})))]))))

(def ->Budget (om/factory Budget))
