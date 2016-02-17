(ns eponai.client.ui.navbar
  (:require [datascript.core :as d]
            [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [eponai.client.ui.add_transaction :refer [->AddTransaction AddTransaction]]
            [eponai.client.ui.add-widget :refer [->NewWidget NewWidget]]
            [eponai.client.ui.dashboard :refer [->Widget]]
            [eponai.client.ui.datepicker :refer [->Datepicker]]
            [eponai.client.ui.tag :as tag]
            [eponai.client.ui.format :as f]
            [eponai.client.ui.utils :as utils]
            [eponai.client.routes :as routes]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]))

;;;;; ###################### Actions ####################

(defn- open-new-menu [component visible]
  (om/update-state! component assoc :new-menu-visible? visible))

(defn- open-profile-menu [component visible]
  (om/update-state! component assoc :menu-visible? visible))

(defn- select-add-transaction [component visible]
  (om/update-state! component assoc :add-transaction? visible))

(defn- select-add-widget [component visible]
  (om/update-state! component assoc :add-widget? visible))

(defn- select-new [component new-id]
  (om/update-state! component assoc new-id true
                    :new-menu-visible? false))

;;;; ##################### UI components ####################

(defn- new-menu [{:keys [on-close on-click]}]
  [:div
   {:class "dropdown open"}
   (utils/click-outside-target on-close)
   [:ul
    {:class "dropdown-menu dropdown-menu-right"}
    [:li [:a {:href "#"
              :on-click #(on-click :add-transaction?)}
          [:i
           (opts {:class "fa fa-usd"
                  :style {:margin-right "0.5em"}})]
          [:span "Transaction"]]]
    [:li.divider]
    [:li [:a {:href     "#"
              :on-click #(on-click :add-widget?)}
          [:i
           (opts {:class "fa fa-bar-chart"
                  :style {:margin-right "0.5em"}})]
          [:span "Widget"]]]]])

;;;; #################### Om Next components #####################

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
    [{:proxy/profile-menu (om/get-query ProfileMenu)}
     {:proxy/add-transaction (om/get-query AddTransaction)}
     {:proxy/add-widget (om/get-query NewWidget)}])
  Object
  (initLocalState [_]
    {:menu-visible? false
     :new-menu-visible? false
     :add-transaction? false
     :add-widget? false})
  (render [this]
    (let [{:keys [proxy/profile-menu
                  proxy/add-transaction
                  proxy/add-widget]} (om/props this)
          {:keys [menu-visible?
                  new-menu-visible?
                  add-transaction?
                  add-widget?]} (om/get-state this)]
      (html
        [:div#navbar-menu
         (opts {:style {:display         "flex"
                        :flex            "row-reverse"
                        :padding         "0.1em"
                        :align-items     "flex-end"
                        :justify-content "flex-end"}})

         [:button
          (opts {:style    {:display "block"
                            :margin  "0.5em 0.2em"
                            :font-size "1em"}
                 :on-click #(open-new-menu this true)
                 :class    "btn btn-default btn-md"})
          [:i
           {:class "fa fa-plus"}]]

         [:img
          (opts {:class    "img-circle"
                 :style    {:margin "0.1em 1em"
                            :width  "40"
                            :height "40"}
                 :src      "/style/img/profile.png"
                 :on-click #(open-profile-menu this true)})]

         (when add-widget?
           (utils/modal {:content  (->NewWidget (om/computed add-widget
                                                             {:on-close     #(select-add-widget this false)}))
                         :on-close #(select-add-widget this false)
                         :class    "modal-lg"}))
         (when menu-visible?
           (->ProfileMenu
             (om/computed profile-menu
                          {:on-close #(open-profile-menu this false)})))
         (when new-menu-visible?
           (new-menu {:on-click  #(select-new this %)
                      :on-close #(open-new-menu this false)}))

         (when add-transaction?
           (let [on-close #(select-add-transaction this false)]
             (utils/modal {:content  (->AddTransaction
                                       (om/computed add-transaction
                                                    {:on-close on-close}))
                           :on-close on-close})))]))))

(def ->NavbarMenu (om/factory NavbarMenu))

;;;; ##################### UI components ####################

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