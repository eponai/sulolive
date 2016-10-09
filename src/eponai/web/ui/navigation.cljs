(ns eponai.web.ui.navigation
  (:require [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
            [eponai.web.ui.add-transaction :refer [->AddTransaction AddTransaction]]
            [eponai.web.ui.add-widget :refer [->NewWidget NewWidget]]
            [eponai.web.ui.widget :refer [->Widget]]
            [eponai.web.ui.datepicker :refer [->Datepicker]]
            [eponai.web.ui.format :as f]
            [eponai.web.ui.icon :as icon]
            [eponai.web.ui.project :as project :refer [->Project]]
            [eponai.web.ui.utils :as utils]
            [eponai.web.routes :as routes]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [datascript.core :as d]
            [taoensso.timbre :refer-macros [debug]]
            [goog.format.EmailAddress]))

;;;; ##################### UI components ####################

(defui NewProject
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

(def ->NewProject (om/factory NewProject))

;;;; #################### Om Next components #####################

(defui NavbarSubmenu
  static om/IQuery
  (query [this]
    [{:query/all-projects [:project/name
                           :project/uuid]}
     {:proxy/add-transaction (om/get-query AddTransaction)}])
  Object
  (initLocalState [this]
    {:on-save-new-project #(.save-new-project this %)
     :on-close-add-transaction #(om/update-state! this assoc :add-transaction? false)})
  (save-new-project [this name]
    (om/transact! this [(list 'project/save {:project/uuid   (d/squuid)
                                             :project/name   name
                                             :dashboard/uuid (d/squuid)})
                        :query/all-projects]))

  (render [this]
    (let [{:keys [app-content]} (om/get-computed this)
          {:keys [menu-visible? new-project? on-save-new-project
                  add-transaction? on-close-add-transaction]} (om/get-state this)

          {:keys [query/all-projects proxy/add-transaction]} (om/props this)

          {:keys [ui.component.project/active-project
                  ui.component.project/selected-tab]} (:query/active-project app-content)
          {project-id :db/id} active-project]
        (debug "Project: " project-id)
      (html
        [:div#subnav
         [:div.row.column
          [:div.top-bar

           ; Project selection and title
           [:div.top-bar-left
            [:span.project-name
             (:project/name active-project)]

            ; Select project dropdonwn menu
            [:div
             (opts {:style {:display "inline-block"}})
             [:a
              (opts {:on-click #(om/update-state! this assoc :menu-visible? true)})
              [:i.fa.fa-caret-down.fa-fw]]
             (when menu-visible?
               (let [on-close #(om/update-state! this assoc :menu-visible? false)]
                 [:div
                  (utils/click-outside-target on-close)
                  [:ul.menu.dropdown.vertical
                   [:li.menu-text.header
                    "Projects"]
                   (map (fn [p]
                          [:li
                           {:key (str (:project/uuid p))}
                           [:a
                            {:on-click #(om/update-state! this assoc :menu-visible? false)
                             :href     (routes/key->route :route/project->dashboard
                                                          {:route-param/project-id (:db/id p)})}
                            (:project/name p)]])
                        all-projects)
                   [:li
                    [:hr]]
                   [:li
                    [:a.secondary-action
                     {:on-click #(om/update-state! this assoc
                                                   :new-project? true
                                                   :menu-visible? false)}
                     [:small "Create new..."]]]]]))]
            ; Add transaction button
            [:a.button.tiny
             {:on-click #(om/update-state! this assoc :add-transaction? true)}
             [:i.fa.fa-plus.fa-fw]]

            (when add-transaction?
              (utils/modal {:content  (->AddTransaction
                                        (om/computed add-transaction
                                                     {:on-close on-close-add-transaction}))
                            :on-close on-close-add-transaction}))]

           ; Project menu
           [:div.top-bar-right
            [:a
             {:href (when project-id
                      (routes/key->route :route/project->dashboard
                                         {:route-param/project-id project-id}))}
             (icon/menu-stats (= selected-tab :dashboard))]
            [:a
             {:href (when project-id
                      (routes/key->route :route/project->txs {:route-param/project-id project-id}))}
             (icon/menu-list (= selected-tab :transactions))]
            [:a
             {:href (when project-id
                      (routes/key->route :route/project->settings
                                         {:route-param/project-id project-id}))}
             (icon/menu-settings (= selected-tab :settings))]]]]

         (when new-project?
           (let [on-close #(om/update-state! this assoc :new-project? false)]
             (utils/modal {:content  (->NewProject (om/computed
                                                     {}
                                                     {:on-close on-close
                                                      :on-save  on-save-new-project}))
                           :on-close on-close})))]))))

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
          [:a#navbar-brand "Jourmoney"]]

         ; Username with user settings menu and sign ut
         [:div.top-bar-right
          (when-not utils/*playground?*
            [:div
             [:a
              {:on-click #(om/update-state! this assoc :menu-visible? true)}
              [:span (:user/email current-user)]
              [:i.fa.fa-caret-down.fa-fw]]

             (when menu-visible?
               (let [on-close #(om/update-state! this assoc :menu-visible? false)]
                 [:div
                  (utils/click-outside-target on-close)
                  [:ul.menu.dropdown
                   [:li
                    [:a
                     {:href     (routes/key->route :route/settings)
                      :on-click on-close}
                     [:span "Settings"]]]
                   [:li [:hr]]
                   [:li
                    [:a.secondary-action
                     {:href     (routes/key->route :route/api->logout)
                      :on-click on-close}
                     [:i.fa.fa-sign-out]
                     [:small
                      "Sign Out"]]]]]))])]

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

(defui Footer
  Object
  (render [this]
    (html
      [:div.footer
       [:small
        "Copyright © eponai 2016. All Rights Reserved"]])))
(def ->Footer (om/factory Footer))

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
             (utils/modal {:content  (->NewProject (om/computed
                                                    {}
                                                    {:on-close on-close
                                                     :on-save  new-project-on-save}))
                           :on-close on-close})))]))))

(def ->SideBar (om/factory SideBar))
