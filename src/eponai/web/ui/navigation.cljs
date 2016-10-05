(ns eponai.web.ui.navigation
  (:require [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [eponai.web.ui.add-transaction :refer [->AddTransaction AddTransaction]]
            [eponai.web.ui.add-widget :refer [->NewWidget NewWidget]]
            [eponai.web.ui.widget :refer [->Widget]]
            [eponai.web.ui.datepicker :refer [->Datepicker]]
            [eponai.web.ui.format :as f]
            [eponai.web.ui.project :as project :refer [->Project]]
            [eponai.web.ui.utils :as utils]
            [eponai.web.routes :as routes]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [datascript.core :as d]
            [taoensso.timbre :refer-macros [debug]]
            [goog.format.EmailAddress]
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
   (utils/click-outside-target on-close)
   [:div.menu.dropdown
    ;{:class "dropdown open"}

    [:a.nav-link
     {:href     (routes/key->route :route/settings)
      :on-click on-close}
     [:i.fa.fa-gear
      (opts {:style {:color "gray"}})]
     [:span.small-caps "Settings"]]
    [:a.nav-link
     {:href     (routes/key->route :route/api->logout)
      :on-click on-close}
     [:i.fa.fa-sign-out]
     [:span.small-caps
      "Sign Out"]]]])

(defui Addproject
  Object
  (render [this]
    (let [{:keys [on-close on-save]} (om/get-computed this)
          {:keys [input-name]} (om/get-state this)]
      (html
        [:div
         [:h3
          "Add project"]
         [:input
          (opts
            {:value       (or input-name "")
             :placeholder "Untitled"
             :type        "text"
             :on-change   #(om/update-state! this assoc :input-name (.-value (.-target %)))
             :style       {:width "100%"}})]
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

(defn submenu-icon [path is-active?]
  [:svg.submenu-icon
   {:xmlns "http://www.w3.org/2000/svg"
    :class (when is-active? "active")
    :width 40
    :height 40
    :viewBox "0 0 40 40"
    :version "1"}
   [:g {:style {:fill-rule "evenodd" :fill "none"}}
    [:path
     {:d path}]]])

(defn icon-stats [& [is-active?]]
  (submenu-icon
    "M18 36L22 36 22 4 18 4 18 36ZM14 40L26 40 26 0 14 0 14 40ZM4 36L8 36 8 12 4 12 4 36ZM0 40L12 40 12 8 0 8 0 40ZM32 36L36 36 36 24 32 24 32 36ZM28 40L40 40 40 20 28 20 28 40Z"
    is-active?))

(defn icon-list [& [is-active?]]
  (submenu-icon
    "M3,28 C1.344,28 0,29.344 0,31 C0,32.656 1.344,34 3,34 C4.656,34 6,32.656 6,31 C6,29.344 4.656,28 3,28 L3,28 Z M10,34 L40,34 L40,30 L10,30 L10,34 Z M3,16 C1.344,16 0,17.344 0,19 C0,20.656 1.344,22 3,22 C4.656,22 6,20.656 6,19 C6,17.344 4.656,16 3,16 L3,16 Z M10,22 L40,22 L40,18 L10,18 L10,22 Z M3,4 C1.344,4 0,5.344 0,7 C0,8.656 1.344,10 3,10 C4.656,10 6,8.656 6,7 C6,5.344 4.656,4 3,4 L3,4 Z M10,10 L40,10 L40,6 L10,6 L10,10 Z"
    is-active?))

(defn icon-settings [& [is-active?]]
  (submenu-icon
    "M22,20 C22,21.104 21.104,22 20,22 C18.896,22 18,21.104 18,20 C18,18.896 18.896,18 20,18 C21.104,18 22,18.896 22,20 M29.64,22.716 C28.754,26.054 26.054,28.754 22.716,29.64 C15.116,31.656 8.344,24.884 10.36,17.284 C11.246,13.946 13.946,11.246 17.284,10.36 C24.884,8.344 31.656,15.116 29.64,22.716 M38,18 L34.688,18 C34.206,18 33.544,17.968 33.548,17.486 C33.558,15.708 32.976,13.18 31.692,11.796 C31.364,11.442 31.46,11.118 31.8,10.778 L34.142,8.562 C34.924,7.78 34.924,6.576 34.142,5.796 C33.362,5.014 32.094,5.046 31.314,5.826 L28.972,8.184 C28.632,8.524 28.106,8.566 27.704,8.302 C26.244,7.338 24.594,6.644 22.82,6.282 C22.348,6.184 22,5.794 22,5.312 L22,2 C22,0.896 21.104,0 20,0 C18.896,0 18,0.896 18,2 L18,5.312 C18,5.794 17.652,6.188 17.18,6.286 C15.406,6.648 13.756,7.344 12.296,8.308 C11.894,8.574 11.368,8.54 11.028,8.2 L8.686,5.858 C7.906,5.076 6.638,5.076 5.858,5.858 C5.076,6.638 5.076,7.906 5.858,8.686 L8.2,11.028 C8.54,11.368 8.592,11.906 8.308,12.296 C7.32,13.662 6.688,15.124 6.404,17.68 C6.35,18.158 5.794,18 5.312,18 L2,18 C0.896,18 0,18.896 0,20 C0,21.104 0.896,22 2,22 L5.312,22 C5.794,22 6.378,22.176 6.474,22.646 C6.836,24.42 7.346,25.744 8.308,27.204 C8.574,27.606 8.54,28.382 8.2,28.722 L5.858,31.188 C5.076,31.97 5.076,33.298 5.858,34.08 C6.638,34.86 7.906,34.892 8.686,34.11 L11.028,31.784 C11.368,31.444 11.894,31.418 12.296,31.684 C13.756,32.646 15.406,33.348 17.18,33.71 C17.652,33.808 18,34.206 18,34.688 L18,38 C18,39.104 18.896,40 20,40 C21.104,40 22,39.104 22,38 L22,34.688 C22,34.206 22.348,33.812 22.82,33.714 C24.594,33.352 26.244,32.654 27.704,31.692 C28.106,31.426 28.632,31.46 28.972,31.8 L31.314,34.142 C32.094,34.924 33.362,34.924 34.142,34.142 C34.924,33.362 34.924,32.094 34.142,31.314 L31.8,28.972 C31.46,28.632 31.426,28.106 31.692,27.704 C32.654,26.244 33.176,25.428 33.714,23.82 C33.868,23.364 34.206,22 34.688,22 L38,22 C39.104,22 40,21.104 40,20 C40,18.896 39.104,18 38,18"
    is-active?))

(defui NavbarSubmenu
  static om/IQuery
  (query [this]
    [{:proxy/project-submenu (or (om/subquery this :submenu project/SubMenu)
                                 (om/get-query project/SubMenu))}])
  Object
  (render [this]
    (let [{:keys [proxy/project-submenu]} (om/props this)
          {:keys [content-factory app-content]} (om/get-computed this)
          project (get-in app-content [:query/active-project :ui.component.project/active-project])
          selected-tab (get-in app-content [:query/active-project :ui.component.project/selected-tab])
          {:keys [db/id]} project]
        (debug "Project: " selected-tab)
      (html
        [:div#subnav
         [:div.top-bar
          [:div.top-bar-left
           [:a.project-name (:project/name project)]]
          [:div.top-bar-right
           [:a
            {:href (when id
                     (routes/key->route :route/project->dashboard
                                        {:route-param/project-id id}))}
            (icon-stats (= selected-tab :dashboard))]
           [:a
            {:href (when id
                     (routes/key->route :route/project->txs {:route-param/project-id id}))}
            (icon-list (= selected-tab :transactions))]
           [:a
            {:href (when id
                     (routes/key->route :route/project->settings
                                        {:route-param/project-id id}))}
            (icon-settings (= selected-tab :settings))]]]]))))

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
  (initLocalState [this]
    {:menu-visible?                     false
     :new-transaction?                  false
     :add-widget?                       false
     :computed/add-transaction-on-close #(om/update-state! this assoc :new-transaction? false)})

  (render [this]
    (let [{:keys [proxy/add-transaction
                  query/current-user
                  query/stripe]} (om/props this)
          {:keys [menu-visible?
                  new-transaction?
                  computed/add-transaction-on-close]} (om/get-state this)
          {:keys [on-sidebar-toggle]} (om/get-computed this)
          {:keys [stripe/subscription]} stripe
          {subscription-status :stripe.subscription/status} subscription]
      (html
        [:div.top-bar#topnav

         [:div.top-bar-left
          [:a "Jourmoney"]]
         [:div.top-bar-right
          [:div.menu-horizontal
           ;(when (= subscription-status :trialing)
           ;  [:small.nav-link
           ;   (str "Trial: " (max 0 (f/days-until (:stripe.subscription/period-end subscription))) " days left")])
           ;(when-not (= subscription-status :active)
           ;  [:div.nav-link.visible-medium-up
           ;   (utils/upgrade-button)])

           (when-not utils/*playground?*
             [:div
              [:a
               {:on-click #(open-profile-menu this true)}
               [:small (:user/email current-user)]]

              (when menu-visible?
                (profile-menu {:on-close #(open-profile-menu this false)}))])]]

         (when new-transaction?
           (utils/modal {:content  (->AddTransaction
                                     (om/computed add-transaction
                                                  {:on-close add-transaction-on-close}))
                         :on-close add-transaction-on-close}))]))))

(defn show-subscribe-modal [component]
  (utils/modal {:on-close #(om/update-state! component assoc :playground/show-subscribe-modal? false)
                :content  (html
                            [:div.subscribe-modal
                             [:h4 "Coming soon"]
                             [:p "We're working hard to make this available to you as soon as possible. In the meantime, subscribe to our newsletter to be notified when we launch."]
                             [:div.subscribe-input
                              [:input
                               (opts {:value       (or (:playground/modal-input (om/get-state component)) "")
                                      :type        "email"
                                      :placeholder "youremail@example.com"
                                      :on-change   #(om/update-state! component assoc :playground/modal-input (.. % -target -value))
                                      :on-key-down #(utils/on-enter-down % (fn [text]
                                                                             (when (goog.format.EmailAddress/isValidAddress text)
                                                                               (om/update-state! component assoc
                                                                                                 :playground/modal-input ""
                                                                                                 :playground/show-subscribe-modal? false)
                                                                               (om/transact! component `[(playground/subscribe ~{:email text})]))))})]
                              [:a.button.warning
                               {:on-click (fn []
                                            (let [text (:playground/modal-input (om/get-state component))]
                                              (when (goog.format.EmailAddress/isValidAddress text)
                                                (om/update-state! component assoc
                                                                  :playground/modal-input ""
                                                                  :playground/show-subscribe-modal? false)
                                                (om/transact! component `[(playground/subscribe ~{:email text})]))))}
                               "Subscribe"]]
                             [:small "Got feedback? We'd love to hear it! Shoot us an email at " [:a.mail-link "info@jourmoney.com"] " and let us know what you'd like to see in the product."]])}))

(def ->NavbarMenu (om/factory NavbarMenu))

(defui SideBar
  static om/IQuery
  (query [_]
    [{:query/all-projects [:project/uuid
                           :project/name
                           :project/created-at
                           :project/users
                           {:project/created-by [:user/uuid
                                                 :user/email]}]}
     {:query/current-user [:user/uuid]}
     {:query/stripe [:stripe/user
                     {:stripe/subscription [:stripe.subscription/status
                                            :stripe.subscription/period-end]}]}
     {:query/active-project [:ui.component.project/uuid]}
     {:query/sidebar [:ui.component.sidebar/newsletter-subscribe-status]}])
  Object
  (initLocalState [this]
    {:new-project? false
     :computed/new-project-on-save #(.save-new-project this %)})
  (save-new-project [this name]
    (om/transact! this [(list 'project/save {:project/uuid   (d/squuid)
                                             :project/name   name
                                             :dashboard/uuid (d/squuid)})
                        :query/all-projects]))
  (render [this]
    (let [{:keys [query/all-projects
                  query/active-project
                  query/current-user
                  query/stripe]} (om/props this)
          {:keys [on-close expanded?]} (om/get-computed this)
          {:keys [new-project? drop-target
                  playground/show-subscribe-modal?
                  computed/new-project-on-save]} (om/get-state this)
          {:keys [stripe/subscription]} stripe
          {subscription-status :stripe.subscription/status} subscription]
      (html
        [:div#sidebar
         (when expanded?
           {:class "expanded"})
         [:div.sidebar-menu.map.map-white.map-left
          [:div
           [:a#navbar-brand
            {:href (routes/key->route :route/home)}
            [:strong
             "JourMoney"]
            [:span
             (if utils/*playground?*
               " Play"
               " App")]]

           [:div.sidebar-submenu
            (when (and (not utils/*playground?*)
                       (= subscription-status :trialing)
                       (:stripe.subscription/period-end subscription))
              [:small.header
               (str (max 0 (f/days-until (:stripe.subscription/period-end subscription))) " days left on trial")])

            (cond
              (true? utils/*playground?*)
              (let [newsletter-status (get-in (om/props this)
                                             [:query/sidebar :ui.component.sidebar/newsletter-subscribe-status])]
                [:div {:style {:text-align "center"}}
                 [:a.upgrade-button
                  (opts {:on-click #(om/update-state! this assoc :playground/show-subscribe-modal? true)})
                  [:strong "I want this!"]]
                 (cond
                   (= newsletter-status :ui.component.sidebar.newsletter-subscribe-status/success)
                   [:small.header "You'll get this!"]

                   (= newsletter-status :ui.component.sidebar.newsletter-subscribe-status/failed)
                   [:small.header "Subscribe error :("])])

              (not= subscription-status :active)
              (utils/upgrade-button))]

           (when show-subscribe-modal?
             (show-subscribe-modal this))

           [:div.sidebar-submenu#project-menu
            [:strong.header "Projects"]

            (map
              (fn [{project-uuid :project/uuid :as project}]
                ;[:li
                ; (opts {:key [project-uuid]})]
                [:a.nav-link
                 {:key           (str project-uuid)
                  :href          (routes/key->route :route/project->dashboard
                                                    {:route-param/project-id (:db/id project)})
                  :class         (cond
                                   (= drop-target project-uuid)
                                   "highlighted"
                                   (= (:ui.component.project/uuid active-project) project-uuid)
                                   "selected")
                  :on-drag-over  #(utils/on-drag-transaction-over this project-uuid %)
                  :on-drag-leave #(utils/on-drag-transaction-leave this %)
                  :on-drop       #(utils/on-drop-transaction this project-uuid %)}
                 [:span.truncate (or (:project/name project) "Untitled")]
                 (when (and (:project/created-by project)
                            (not (= (:user/uuid (:project/created-by project)) (:user/uuid current-user))))
                   [:small " by " (:user/email (:project/created-by project))])])
              all-projects)

            ;[:li]
            [:a.nav-link
             {:on-click #(om/update-state! this assoc :new-project? true)}
             [:i.fa.fa-plus.fa-fw
              (opts {:style {:padding 0}})]
             [:span.small-caps "New..."]]]]

          (when-not utils/*playground?*
            [:div.sidebar-submenu.hidden-medium-up
             [:a.nav-link
              (opts {:href (routes/key->route :route/settings)})
              [:i.fa.fa-gear
               (opts {:style {:display :inline
                              :padding "0.5em"}})]
              "Settings"]

             [:a.nav-link
              (opts {:href (routes/key->route :route/api->logout)})
              "Sign Out"]])
          [:footer.footer
           [:p.copyright.small.text-light
            "Copyright © eponai 2016. All Rights Reserved"]]]

         (when new-project?
           (let [on-close #(om/update-state! this assoc :new-project? false)]
             (utils/modal {:content  (->Addproject (om/computed
                                                    {}
                                                    {:on-close on-close
                                                     :on-save  new-project-on-save}))
                           :on-close on-close})))]))))

(def ->SideBar (om/factory SideBar))
