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
    {:class "dropdown-menu dropdown-menu-left"}
    [:li [:a
          (opts {:href     "#"
                 :on-click #(on-click :add-transaction?)
                 :style    {:padding "0.5em"}})
          [:i
           (opts {:class "fa fa-usd"
                  :style {:width "20%"}})]
          [:span "Transaction"]]]
    [:li
      [:a
       (opts {:href     "#"
              :on-click #(on-click :add-widget?)
              :style {:padding "0.5em"}})
       [:i
           (opts {:class "fa fa-bar-chart"
                  :style {:width "20%"}})]
          [:span "Widget"]]]]])

(defn- profile-menu [{:keys [on-close]}]
  [:div
   {:class "dropdown open"}
   (utils/click-outside-target on-close)
   [:ul
    {:class "dropdown-menu dropdown-menu-right"}
    [:li
     [:a
      (opts {:href     "#"
             :on-click on-close
             :style    {:padding "0.5em"}})
      [:i
       (opts {:class "fa fa-user"
              :style {:width "15%"}})]
      [:span "Profile"]]]
    [:li [:a
          (opts {:href     (routes/inside "/settings")
                 :on-click on-close
                 :style    {:padding "0.5em"}})
          [:i
           (opts {:class "fa fa-gear"
                  :style {:width "15%"}})]
          [:span "Settings"]]]
    [:li.divider]
    [:li [:a
          (opts {:href     (routes/outside "/api/logout")
                 :on-click on-close
                 :style    {:padding "0.5em"}})
          "Sign Out"]]]])

;;;; #################### Om Next components #####################


(defui NavbarMenu
  static om/IQuery
  (query [_]
    [{:proxy/add-transaction (om/get-query AddTransaction)}
     {:proxy/add-widget (om/get-query NewWidget)}
     {:query/current-user [:user/uuid
                           :user/activated-at]}])
  Object
  (initLocalState [_]
    {:menu-visible? false
     :new-menu-visible? false
     :add-transaction? false
     :add-widget? false})
  (render [this]
    (let [{:keys [proxy/add-transaction
                  proxy/add-widget
                  query/current-user]} (om/props this)
          {:keys [menu-visible?
                  new-menu-visible?
                  add-transaction?
                  add-widget?]} (om/get-state this)
          {:keys [sidebar-visible?
                  on-sidebar-show]} (om/get-computed this)]
      (html
        [:div
         (opts {:style {:display         "flex"
                        :flex            "row"
                        :padding         "0.1em"
                        :align-items     "flex-end"
                        :justify-content :space-between}})

         [:div
          (opts {:style {:display :flex
                         :flex-direction :row}})
          (when-not sidebar-visible?
            [:button
             (opts {:style    {:display   "block"
                               :margin    "0.5em 0.2em"
                               :font-size "1em"}
                    :on-click #(do (prn "Did show sidebar")
                                   (on-sidebar-show))
                    :class    "btn btn-default btn-md"})
             [:i
              {:class "fa fa-bars"}]])

          [:button
           (opts {:style    {:display   "block"
                             :margin    "0.5em 0.2em"
                             :font-size "1em"}
                  :on-click #(open-new-menu this true)
                  :class    "btn btn-info btn-md"})
           [:i
            {:class "fa fa-plus"}]]
          (when new-menu-visible?
            (new-menu {:on-click #(select-new this %)
                       :on-close #(open-new-menu this false)}))]

         [:div
          (opts {:style {:display        :flex
                         :flex-direction :row
                         :align-items    :flex-end}})
          (let [activated-at (:user/activated-at current-user)]
            [:p.small.text-muted
             (if activated-at
               (str "Trial: " (max 0 (- 14 (f/days-since activated-at))) " days left")
               (str "Trial ended"))])
          [:a (opts {:style {:display "block"
                             :margin  "0.5em 0.2em"}
                     :class "btn btn-primary btn-md"
                     :href  (routes/inside "/subscribe/")})
           "Upgrade"]
          [:a
           {:href     "#"}
           [:img
            (opts {:class    "img-circle"
                   :style    {:margin "0.1em 1em"
                              :width  "40"
                              :height "40"}
                   :src      "/style/img/profile.png"
                   :on-click #(open-profile-menu this true)})]]

          (when menu-visible?
            (profile-menu {:on-close #(open-profile-menu this false)}))]

         (when add-widget?
           (utils/modal {:content  (->NewWidget (om/computed add-widget
                                                             {:on-close #(select-add-widget this false)}))
                         :on-close #(select-add-widget this false)
                         :class    "modal-lg"}))
         (when add-transaction?
           (let [on-close #(select-add-transaction this false)]
             (utils/modal {:content  (->AddTransaction
                                       (om/computed add-transaction
                                                    {:on-close on-close}))
                           :on-close on-close})))]))))

(def ->NavbarMenu (om/factory NavbarMenu))

(defui SideBar
  static om/IQuery
  (query [_]
    [{:query/all-budgets [:budget/uuid
                          :budget/name]}
     {:query/current-user [:user/uuid
                           :user/activated-at]}])
  Object

  (render [this]
    (let [{:keys [query/all-budgets]} (om/props this)
          {:keys [on-sidebar-close]} (om/get-computed this)]
      (html
        [:ul.sidebar-nav
         [:li.sidebar-brand
          (opts {:style {:display :flex
                         :flex-direction :row}})

          [:a.navbar-brand
           {:href (routes/inside "/")}
           [:strong
            "JourMoney"]
           [:span.small
            " by eponai"]]
          [:button.close.navbar-brand
           (opts {:style    {:margin "0 auto"}
                  :on-click #(do (prn "Did click close")
                                 (on-sidebar-close))})
           "X"]]

         [:li
          [:a {:href (routes/inside "/transactions")
               :on-click nil}
           "All Transactions"]]

         (map
           (fn [budget]
             [:li
              (opts {:key [(:budget/uuid budget)]})
              [:a {:href     (routes/inside "/dashboard/" (:budget/uuid budget))}
               (or (:budget/name budget) "Untitled")]])
           all-budgets)]))))

(def ->SideBar (om/factory SideBar))

;;;; ##################### UI components ####################

(defn navbar-query []
  (om/get-query NavbarMenu))

(defn sidebar-query []
  (om/get-query SideBar))

(defn sidebar-create [props computed]
  [:div#sidebar-wrapper.gradient-down-up
   [:div#content
    (->SideBar (om/computed props
                            computed))]
   [:footer.footer
    [:div.container
     [:p.copyright.small.text-light
      "Copyright © eponai 2016. All Rights Reserved"]]]])

(defn navbar-create [props computed]
  [:div
   [:nav
    (opts {:class "navbar navbar-default navbar-fixed-top topnav"
           :role  "navigation"})
    (->NavbarMenu (om/computed props
                               computed))]])