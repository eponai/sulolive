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

(defui NavbarSubmenu
  static om/IQuery
  (query [this]
    [{:proxy/project-submenu (or (om/subquery this :submenu project/SubMenu)
                                 (om/get-query project/SubMenu))}])
  Object
  (render [this]
    (let [{:keys [proxy/project-submenu]} (om/props this)
          {:keys [content-factory app-content]} (om/get-computed this)]
      (html
        [:div#sub-nav-bar
         [:div
          (when (= content-factory ->Project)
                (project/->SubMenu (om/computed (assoc project-submenu :ref :submenu)
                                                {:app-content app-content})))]]))))

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
        [:div#top-nav-bar

         [:div.menu-horizontal
          [:div.top-bar-link
           [:a.nav-link.hidden-xlarge-up
            {:on-click #(do (prn "Did show sidebar")
                            (on-sidebar-toggle))}
            [:i.fa.fa-bars]]]
          ;[:a.nav-link.button.success.tiny
          ; {:on-click #(do
          ;              ;(.track js/mixpanel "navigation/NewTransaction" {:user                (:user/uuid current-user)
          ;              ;                                                 :subscription-status subscription-status})
          ;              (om/update-state! this assoc :new-transaction? true))}
          ; [:i.fa.fa-plus]]
          ]

         [:div.menu-horizontal
          ;(when (= subscription-status :trialing)
          ;  [:small.nav-link
          ;   (str "Trial: " (max 0 (f/days-until (:stripe.subscription/period-end subscription))) " days left")])
          ;(when-not (= subscription-status :active)
          ;  [:div.nav-link.visible-medium-up
          ;   (utils/upgrade-button)])

          (when-not utils/*playground?*
            [:div.nav-link
             [:div.top-bar-link
              [:a
               {:on-click #(open-profile-menu this true)}
               [:small (:user/email current-user)]]

              (when menu-visible?
                (profile-menu {:on-close #(open-profile-menu this false)}))]])]

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
             " App"]]

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
