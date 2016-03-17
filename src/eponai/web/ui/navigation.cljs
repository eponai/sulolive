(ns eponai.web.ui.navigation
  (:require [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [eponai.web.ui.add-transaction :refer [->AddTransaction AddTransaction]]
            [eponai.web.ui.add-widget :refer [->NewWidget NewWidget]]
            [eponai.web.ui.widget :refer [->Widget]]
            [eponai.web.ui.datepicker :refer [->Datepicker]]
            [eponai.web.ui.format :as f]
            [eponai.web.ui.utils :as utils]
            [eponai.web.routes :as routes]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [datascript.core :as d]))

;;;;; ###################### Actions ####################

(defn- open-new-menu [component visible]
  (om/update-state! component assoc :new-menu-visible? visible))

(defn- open-profile-menu [component visible]
  (om/update-state! component assoc :menu-visible? visible))


(defn- select-new [component new-id]
  (om/update-state! component assoc new-id true
                    :new-menu-visible? false))



;;;; ##################### UI components ####################

(defn- new-menu [{:keys [on-close on-click]}]
  [:div
   {:class "dropdown-pane is-open"}
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
             :on-click #(on-click :add-budget?)
             :style {:padding "0.5em"}})
      [:i
       (opts {:class "fa fa-file-text-o"
              :style {:width "20%"}})]
      [:span "Budget"]]]]])

(defn- profile-menu [{:keys [on-close]}]
  [:div
   ;{:class "dropdown open"}
   (utils/click-outside-target on-close)
   [:ul.dropdown-menu
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

(defui AddBudget
  Object
  (render [this]
    (let [{:keys [on-close
                  on-save]} (om/get-computed this)
          {:keys [input-name]} (om/get-state this)]
      (html
        [:div
         [:h3
          "Add budget"]
         [:input
          (opts
            {:value       input-name
             :placeholder "Untitled"
             :type        "text"
             :on-change   #(om/update-state! this assoc :input-name (.-value (.-target %)))
             :style {:width "100%"}})]
         ;[:br]
         [:div.inline-block
          (opts {:style {:float :right}})
          [:a.button.secondary
           {:on-click on-close}
           "Cancel"]
          [:a.button
           {:on-click #(do
                        (on-save input-name)
                        (on-close))}
           "Save"]]]))))

(def ->AddBudget (om/factory AddBudget))

;;;; #################### Om Next components #####################


(defui NavbarMenu
  static om/IQuery
  (query [_]
    [{:proxy/add-transaction (om/get-query AddTransaction)}
     {:query/current-user [:user/uuid
                           :user/activated-at
                           :user/picture]}])
  Object
  (initLocalState [_]
    {:menu-visible? false
     :new-transaction? false
     :add-widget? false})
  (render [this]
    (let [{:keys [proxy/add-transaction
                  query/current-user]} (om/props this)
          {:keys [menu-visible?
                  new-transaction?]} (om/get-state this)
          {:keys [on-sidebar-toggle]} (om/get-computed this)]
      (html
        [:div
         [:nav#navbar-menu
          (opts {:class "top-bar"
                 :style {:position   :fixed
                         :z-index 1000
                         :top        0
                         :right      0
                         :left       0
                         :background :white
                         :border     "1px solid #e7e7e7"}})
          [:div
           {:class "top-bar-left"}
           [:ul.menu
            [:li
             [:a#sidebar-toggle
              {:on-click #(do (prn "Did show sidebar")
                              (on-sidebar-toggle))}
              [:i
               {:class "fa fa-bars"}]]]
            [:li
             [:a
              {:on-click #(om/update-state! this assoc :new-transaction? true)
               :class    "button primary medium"}
              [:i.fa.fa-plus]
              "New transaction"]]]]

          [:div
           {:class "top-bar-right"}
           [:ul.dropdown.menu
            (let [activated-at (:user/activated-at current-user)]
              [:li
               (if activated-at
                 (str "Trial: " (max 0 (- 14 (f/days-since activated-at))) " days left")
                 (str "Trial ended"))])
            [:li
             [:a {:class "button warning medium"
                  :href  (routes/inside "/subscribe/")}
              "Upgrade"]]
            [:li.has-submenu
             [:img
              (opts {:class    "img-circle"
                     :style    {:width         "40"
                                :height        "40"
                                :border-radius "50%"}
                     :src      (or (:user/picture current-user) "/style/img/profile.png")
                     :on-click #(open-profile-menu this true)})]

             (when menu-visible?
               (profile-menu {:on-close #(open-profile-menu this false)}))]]]]

         (when new-transaction?
           (let [on-close #(om/update-state! this assoc :new-transaction? false)]
             (utils/modal {:content  (->AddTransaction
                                       (om/computed add-transaction
                                                    {:on-close on-close}))
                           :on-close on-close})))]))))

(def ->NavbarMenu (om/factory NavbarMenu))

(defui SideBar
  static om/IQuery
  (query [_]
    [{:query/all-budgets [:budget/uuid
                          :budget/name
                          :budget/created-at]}
     {:query/current-user [:user/uuid
                           :user/activated-at]}])
  Object
  (initLocalState [_]
    {:new-budget? false})
  (save-new-budget [this name]
    (om/transact! this `[(budget/save ~{:budget/uuid (d/squuid)
                                        :budget/name name
                                        :dashboard/uuid (d/squuid)
                                        :mutation-uuid (d/squuid)})
                         :query/all-budgets]))
  (render [this]
    (let [{:keys [query/all-budgets]} (om/props this)
          {:keys [on-close]} (om/get-computed this)
          {:keys [new-budget?]} (om/get-state this)]
      (html
        [:div
         [:div
          (when on-close
            (opts {:class    "reveal-overlay"
                   :id       "click-outside-target-id"
                   :style    {:display (if on-close "block" "none")}
                   :on-click #(when (= "click-outside-target-id" (.-id (.-target %)))
                               (on-close))}))]
         [:div#sidebar.gradient-down-up
          [:div#content
           [:ul.sidebar-nav
            [:li.sidebar-brand
             (opts {:style {:display        :flex
                            :flex-direction :row}})

             [:a.navbar-brand
              {:href (routes/inside "/")}
              [:strong
               "JourMoney"]
              [:span.small
               " by eponai"]]]

            [:li
             [:a
              (opts {:href (routes/inside "/profile")})
              [:i.fa.fa-user
               (opts {:style {:display :inline
                              :padding "0.5em"}})]
              [:strong "Profile"]]]

            (map
              (fn [budget]
                [:li
                 (opts {:key [(:budget/uuid budget)]})
                 [:a {:href (routes/inside "/dashboard/" (:budget/uuid budget))}
                  (or (:budget/name budget) "Untitled")]])
              all-budgets)

            [:li
             [:a.secondary
              {:on-click #(om/update-state! this assoc :new-budget? true)}
              [:i.fa.fa-plus
               (opts {:style {:display :inline
                              :padding "0.5em"}})]
              "New..."]]]]
          [:footer.footer
           [:p.copyright.small.text-light
            "Copyright © eponai 2016. All Rights Reserved"]]]

         (when new-budget?
           (let [on-close #(om/update-state! this assoc :new-budget? false)]
             (utils/modal {:content  (->AddBudget (om/computed
                                                    {}
                                                    {:on-close on-close
                                                     :on-save  #(.save-new-budget this %)}))
                           :on-close on-close})))]))))

(def ->SideBar (om/factory SideBar))
