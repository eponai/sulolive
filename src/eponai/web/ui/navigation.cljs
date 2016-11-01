(ns eponai.web.ui.navigation
  (:require
    [datascript.core :as d]
    [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
    [eponai.web.ui.project.add-project :refer [->NewProject]]
    [eponai.web.ui.project.add-transaction :refer [->AddTransaction AddTransaction]]
    [eponai.web.ui.settings :refer [->Settings Settings]]
    [eponai.web.ui.icon :as icon]
    [eponai.web.ui.utils :as utils]
    [eponai.web.routes :as routes]
    [garden.core :refer [css]]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]))

;;;; #################### Om Next components #####################

(defui NavbarSubmenu
  static om/IQuery
  (query [this]
    [{:query/all-projects [:project/users
                           :project/name
                           :project/uuid]}
     {:proxy/add-transaction (om/get-query AddTransaction)}])
  Object
  (initLocalState [this]
    {:on-save-new-project #(.save-new-project this %)
     :on-close-add-transaction #(om/update-state! this assoc :add-transaction? false)
     :add-transaction? false})
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
      (html
        [:div
         [:div.top-bar-container.subnav#subnav
          [:div.row.column
           [:div.top-bar

            ; Project selection and title
            [:div.top-bar-left
             [:span.header.project-name
              (:project/name active-project)]

             ; Select project dropdonwn menu
             [:div
              (opts {:style {:display "inline-block"}})
              [:a.navbar-menu-item
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
              {:class (when-not (seq all-projects) "disabled")
               :on-click #(om/update-state! this assoc :add-transaction? true)}
              [:i.fa.fa-plus.fa-fw]]

             ]

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
              (icon/menu-settings (= selected-tab :settings))]]]]]

         (when add-transaction?
           (utils/modal {:content  (->AddTransaction
                                     (om/computed add-transaction
                                                  {:on-close on-close-add-transaction
                                                   :project-id project-id
                                                   :project-name (:project/name active-project)}))
                         :on-close on-close-add-transaction}))
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
     {:proxy/settings (om/get-query Settings)}
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
     ;:settings-open? true
     :computed/add-transaction-on-close #(om/update-state! this assoc :new-transaction? false)})

  (render [this]
    (let [{:keys [proxy/add-transaction
                  proxy/settings
                  query/current-user
                  query/stripe]} (om/props this)
          {:keys [menu-visible?
                  new-transaction?
                  settings-open?
                  computed/add-transaction-on-close]} (om/get-state this)
          {:keys [stripe/subscription]} stripe
          {subscription-status :stripe.subscription/status} subscription]
      (html
        [:div.top-bar-container#topnav
         [:div.top-bar

          [:div.top-bar-left
           [:a.navbar-brand "Jourmoney"]]

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
                   [:ul.menu.dropdown.vertical
                    [:li
                     [:a
                      {:on-click #(om/update-state! this assoc
                                   :settings-open? true
                                   :menu-visible? false)}
                      [:span "Settings"]]]
                    [:li [:hr]]
                    [:li
                     [:a.secondary-action
                      {:href     (routes/key->route :route/api->logout)
                       :on-click on-close}
                      [:i.fa.fa-sign-out]
                      [:small
                       "Sign Out"]]]]]))])]

          (when settings-open?
            (utils/modal {:content  (->Settings (om/computed settings
                                                             {:on-close #(om/update-state! this assoc :settings-open? false)}))
                          :on-close #(om/update-state! this assoc :settings-open? false)}))
          (when new-transaction?
            (utils/modal {:content  (->AddTransaction
                                      (om/computed add-transaction
                                                   {:on-close add-transaction-on-close}))
                          :on-close add-transaction-on-close}))]]))))

(def ->NavbarMenu (om/factory NavbarMenu))

(defui Footer
  Object
  (render [_]
    (html
      [:div.footer
       [:small
        "Copyright © eponai 2016. All Rights Reserved"]])))
(def ->Footer (om/factory Footer))
