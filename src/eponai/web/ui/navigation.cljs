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
            [datascript.core :as d]
            [taoensso.timbre :refer-macros [debug]]
            [eponai.common.format :as format]))

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
             :on-click #(on-click :add-project?)
             :style {:padding "0.5em"}})
      [:i
       (opts {:class "fa fa-file-text-o"
              :style {:width "20%"}})]
      [:span "project"]]]]])

(defn- profile-menu [{:keys [on-close]}]
  [:div
   ;{:class "dropdown open"}
   (utils/click-outside-target on-close)
   [:ul.dropdown-menu
    [:li [:a
          (opts {:href     (routes/key->route :route/settings)
                 :on-click on-close
                 :style    {:padding "0.5em"}})
          [:i
           (opts {:class "fa fa-gear"
                  :style {:width "15%"}})]
          [:span "Settings"]]]
    [:li.divider]
    [:li [:a
          (opts {:href     (routes/key->route :route/api->logout)
                 :on-click on-close
                 :style    {:padding "0.5em"}})
          "Sign Out"]]]])

(defui Addproject
  Object
  (render [this]
    (let [{:keys [on-close
                  on-save]} (om/get-computed this)
          {:keys [input-name]} (om/get-state this)]
      (html
        [:div
         [:h3
          "Add project"]
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

(def ->Addproject (om/factory Addproject))

;;;; #################### Om Next components #####################

(defui NavbarSubmenu
  Object
  (componentDidMount [this]
    (let [navbar (.getElementById js/document "navbar-menu")
          top (if navbar
                (.-offsetHeight navbar)
                0)]
      (om/update-state! this assoc :top top)))
  (render [this]
    (let [{:keys [content]} (om/get-computed this)
          {:keys [top]} (om/get-state this)]
      (html
        [:div#navbar-submenu
         (opts {:style {:background "#fff"
                        :border "1px solid #e7e7e7"
                        ;:width    "100%"
                        ;:left :inherit
                        ;:right 0
                        :z-index  999
                        ;:position :fixed
                        }})
         content]))))

(def ->NavbarSubmenu (om/factory NavbarSubmenu))

(defui NavbarMenu
  static om/IQuery
  (query [_]
    [{:proxy/add-transaction (om/get-query AddTransaction)}
     {:query/current-user [:user/uuid
                           :user/email]}
     {:query/stripe [:stripe/user
                     {:stripe/subscription [:stripe.subscription/status
                                            :stripe.subscription/period-end]}]}])
  Object
  (initLocalState [_]
    {:menu-visible? false
     :new-transaction? false
     :add-widget? false})
  (render [this]
    (let [{:keys [proxy/add-transaction
                  query/current-user
                  query/stripe]} (om/props this)
          {:keys [menu-visible?
                  new-transaction?]} (om/get-state this)
          {:keys [on-sidebar-toggle]} (om/get-computed this)
          {:keys [stripe/subscription]} stripe
          {subscription-status :stripe.subscription/status} subscription]
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
             [:a.button.success.small
              {:on-click #(do
                           (.track js/mixpanel "navigation/NewTransaction" {:user (:user/uuid current-user)
                                                                            :subscription-status subscription-status})
                           (om/update-state! this assoc :new-transaction? true))}
              [:i.fa.fa-plus]
              [:span "New Transaction"]]]]]

          [:div.top-bar-right.profile-menu
           [:ul.dropdown.menu
            (when (= subscription-status :trialing)
              [:li
               [:small
                (str "Trial: " (max 0 (f/days-until (:stripe.subscription/period-end subscription))) " days left")]])
            (when-not (= subscription-status :active)
              [:li
               (utils/upgrade-button
                 {:href     (routes/key->route :route/subscribe)})])
            ;[:li
            ; [:a
            ;  [:i.fa.fa-bell]]]
            [:li.has-submenu
             [:a.button.hollow.secondary.small
              {:on-click #(open-profile-menu this true)}
              [:small (:user/email current-user)]]
             ;[:img
             ; (opts {:class    "img-circle"
             ;        :style    {:width         40
             ;                   :height        40
             ;                   :border-radius "50%"}
             ;        :src      "/style/img/profile.png"
             ;        :on-click #(open-profile-menu this true)})]

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
    [{:query/all-projects [:project/uuid
                          :project/name
                          :project/created-at
                          {:project/created-by [:user/uuid
                                               :user/email]}]}
     {:query/current-user [:user/uuid]}
     {:query/stripe [:stripe/user
                     {:stripe/subscription [:stripe.subscription/status]}]}
     {:query/active-project [:ui.component.project/uuid]}])
  Object
  (initLocalState [_]
    {:new-project? false})
  (save-new-project [this name]
    (om/transact! this `[(project/save ~{:project/uuid (d/squuid)
                                        :project/name name
                                        :dashboard/uuid (d/squuid)
                                        :mutation-uuid (d/squuid)})
                         :query/all-projects]))
  (render [this]
    (let [{:keys [query/all-projects
                  query/active-project
                  query/current-user
                  query/stripe]} (om/props this)
          {:keys [on-close]} (om/get-computed this)
          {:keys [new-project? drop-target]} (om/get-state this)
          {:keys [stripe/subscription]} stripe
          {subscription-status :stripe.subscription/status} subscription]
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
              {:href (routes/key->route :route/home)}
              [:strong
               "JourMoney"]
              [:small
               " by eponai"]]]

            (when-not (= subscription-status :active)
              [:li.profile-menu
               (utils/upgrade-button
                 {:href     (routes/key->route :route/subscribe)
                  :style {:margin "1em"}})])
            ;(when (= subscription-status :trialing)
            ;  [:li.profile-menu
            ;   (utils/upgrade-button {:on-click on-close
            ;                          :style    {:margin "0.5em"}})])
            ;[:li
            ; [:a
            ;  (opts {:href (routes/key->route :route/profile)
            ;         :on-click on-close})
            ;  [:i.fa.fa-user
            ;   (opts {:style {:display :inline
            ;                  :padding "0.5em"}})]
            ;  [:strong "Profile"]]]
            [:li.divider]
            [:li
             [:small [:strong "Projects"]]]

            (map
              (fn [{project-uuid :project/uuid :as project}]
                [:li
                 (opts {:key [project-uuid]})
                 [:a {:href          (routes/key->route :route/project->dashboard
                                                        {:route-param/project-id (:db/id project)})
                      :class         (cond
                                       (= drop-target project-uuid)
                                       "highlighted"
                                       (= (:ui.component.project/uuid active-project) project-uuid)
                                       "selected")
                      :on-click      on-close
                      :on-drag-over  #(utils/on-drag-transaction-over this project-uuid %)
                      :on-drag-leave #(utils/on-drag-transaction-leave this %)
                      :on-drop       #(utils/on-drop-transaction this project-uuid %)}
                  [:span (or (:project/name project) "Untitled")]
                  (when (and (:project/created-by project)
                             (not (= (:user/uuid (:project/created-by project)) (:user/uuid current-user))))
                    [:small " by " (:user/email (:project/created-by project))])]])
              all-projects)

            [:li
             [:a.secondary
              {:on-click #(om/update-state! this assoc :new-project? true)}
              [:i.fa.fa-plus
               (opts {:style {:display :inline
                              :padding "0.5em"}})]
              [:small "New..."]]]

            [:li.divider]
            [:li.profile-menu
             [:a
              (opts {:href     (routes/key->route :route/settings)
                     :on-click on-close})
              [:i.fa.fa-gear
               (opts {:style {:display :inline
                              :padding "0.5em"}})]
              "Settings"]]

            [:li.profile-menu
             [:a
              (opts {:href     (routes/key->route :route/api->logout)
                     :on-click on-close})
              "Sign Out"]]]]
          [:footer.footer
           [:p.copyright.small.text-light
            "Copyright © eponai 2016. All Rights Reserved"]]]

         (when new-project?
           (let [on-close #(om/update-state! this assoc :new-project? false)]
             (utils/modal {:content  (->Addproject (om/computed
                                                    {}
                                                    {:on-close on-close
                                                     :on-save  #(.save-new-project this %)}))
                           :on-close on-close})))]))))

(def ->SideBar (om/factory SideBar))
