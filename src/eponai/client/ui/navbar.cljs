(ns eponai.client.ui.navbar
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer-macros [opts map-all]]
            [eponai.client.ui.format :as f]
            [eponai.client.ui.utils :as utils]
            [eponai.client.routes :as routes]
            [eponai.client.ui.add_transaction :refer [AddTransaction ->AddTransaction]]
            [sablono.core :refer-macros [html]]
            [garden.core :refer [css]]))

(defui ProfileMenu
  static om/IQuery
  (query [_]
    [{:query/all-budgets [:budget/uuid
                          :budget/name]}
     {:query/current-user [:user/uuid
                           :user/activated-at]}])
  Object
  (render [this]
    (let [{:keys [query/all-budgets
                  query/current-user]} (om/props this)
          {:keys [on-close]} (om/get-computed this)]
      (html
        [:div
         {:class "dropdown open"}
         (utils/click-outside-target on-close)
         [:ul
          {:class "dropdown-menu dropdown-menu-right"}
          [:li.dropdown-header
           (let [activated-at (:user/activated-at current-user)]
             (if activated-at
               (str "Trial: " (max 0 (- 14 (f/days-since activated-at))) " days left")
               (str "Trial ended")))]
          [:li [:a (opts {:style {:display "block"
                                  :margin  "0.5em 0.2em"}
                          :class "btn btn-primary btn-md"
                          :href  (routes/inside "/subscribe/")
                          :on-click on-close})
                "Buy"]]
          [:li.divider]

          (when (not-empty all-budgets)
            [:li.dropdown-header
             "Sheets"])
          (map
            (fn [budget]
              [:li
               (opts {:key [(:budget/uuid budget)]})
               [:a {:href (routes/inside "/dashboard/" (:budget/uuid budget))
                    :on-click on-close}
                (or (:budget/name budget) "Untitled")]])
            all-budgets)

          [:li.divider]
          [:li [:a {:href (routes/inside "/transactions")
                    :on-click on-close}
                "All Transactions"]]
          [:li.divider]
          [:li [:a {:href     "#"
                    :on-click on-close}
                "Profile"]]
          [:li [:a {:href (routes/inside "/settings")
                    :on-click on-close}
                "Settings"]]
          [:li.divider]
          [:li [:a {:href (routes/outside "/api/logout")
                    :on-click on-close}
                "Sign Out"]]]]))))

(def ->ProfileMenu (om/factory ProfileMenu))

(defui NavbarMenu
  static om/IQuery
  (query [_]
    [{:proxy/popup-menu (om/get-query ProfileMenu)}
     {:proxy/add-transaction (om/get-query AddTransaction)}])
  Object
  (add-transaction [this visible]
    (om/update-state! this assoc :add-transaction-visible visible))
  (open-menu [this visible]
    (om/update-state! this assoc :menu-visible visible))
  (initLocalState [_]
    {:menu-visible false
     :add-transaction-visible false})
  (render [this]
    (let [{:keys [proxy/popup-menu
                  proxy/add-transaction]} (om/props this)
          {:keys [menu-visible
                  add-transaction-visible]} (om/get-state this)]
      (html
        [:div#navbar-menu
         (opts {:style {:display         "flex"
                        :flex            "row-reverse"
                        :align-items     "flex-end"
                        :justify-content "flex-end"}})

         [:button
          (opts {:style    {:display "block"
                            :margin  "0.5em 0.2em"}
                 :on-click #(.add-transaction this true)
                 :class    "btn btn-default btn-md"})
          "New"]

         [:img
          (opts {:class    "img-circle"
                 :style    {:margin "0.1em 1em"
                            :width  "40"
                            :height "40"}
                 :src      "/style/img/profile.png"
                 :on-click #(.open-menu this true)})]

         (when menu-visible
           (->ProfileMenu
             (om/computed popup-menu
                          {:on-close #(.open-menu this false)})))

         (when add-transaction-visible
           (let [on-close #(.add-transaction this false)]
             (utils/modal {:content  (->AddTransaction
                                       (om/computed add-transaction
                                                    {:on-close on-close}))
                           :on-close on-close})))]))))

(def ->NavbarMenu (om/factory NavbarMenu))

(defn navbar-query []
  (om/get-query NavbarMenu))

(defn navbar-create [props]
  [:nav
   (opts {:class "navbar navbar-default navbar-fixed-top topnav"
          :role  "navigation"})
   [:div
    [:a.navbar-brand
     {:href (routes/inside "/")}
     [:strong
      "JourMoney"]
     [:span.small
      " by eponai"]]]
   (->NavbarMenu props)])