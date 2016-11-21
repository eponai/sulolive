(ns eponai.web.ui.navigation
  (:require
    [datascript.core :as d]
    [eponai.client.ui :refer [map-all] :refer-macros [style opts]]
    [eponai.web.ui.project.add-project :refer [->NewProject]]
    [eponai.web.ui.project.add-transaction :refer [->AddTransaction AddTransaction]]
    [eponai.web.ui.settings :refer [->Settings Settings]]
    [eponai.web.ui.utils.button :as button]
    [eponai.web.ui.icon :as icon]
    [eponai.web.ui.utils :as utils]
    [eponai.web.routes :as routes]
    [garden.core :refer [css]]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [error debug]]))

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
                (utils/dropdown
                  {:on-close #(om/update-state! this assoc :menu-visible? false)}
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
                   [(if utils/*playground?*
                      :a.secondary-action.disabled
                      :a.secondary-action)
                    {:on-click #(om/update-state! this assoc
                                                  :new-project? true
                                                  :menu-visible? false)}
                    [:small "Create new..."]]]))]
             ; Add transaction button
             ((-> button/button button/tiny)
               {:on-click #(om/update-state! this assoc :add-transaction? true)
                :class (when-not (seq all-projects) "disabled")}
               [:div.has-tip
                {:data-tooltip  true
                 :aria-haspopup true
                 :title         "New Transaction"}
                [:i.fa.fa-plus.fa-fw]])]

            ; Project menu
            [:div.top-bar-right
             [:a
              {:href (when project-id
                       (routes/key->route :route/project->dashboard
                                          {:route-param/project-id project-id}))}

              [:div.has-tip
               {:data-tooltip  true
                :aria-haspopup true
                :title "Dashboard"}
               (icon/menu-stats (= selected-tab :dashboard))]]
             [:a
              {:href (when project-id
                       (routes/key->route :route/project->txs {:route-param/project-id project-id}))}
              [:div.has-tip
               {:data-tooltip  true
                :aria-haspopup true
                :title "All Transactions"}
               (icon/menu-list (= selected-tab :transactions))]]
             (when-not utils/*playground?*
               [:a
               {:href (when project-id
                        (routes/key->route :route/project->settings
                                           {:route-param/project-id project-id}))}
                [:div.has-tip
                 {:data-tooltip  true
                  :aria-haspopup true
                  :title "Settings"}
                 (icon/menu-settings (= selected-tab :settings))]])]]]]

         (when add-transaction?
           (utils/modal {:content  (->AddTransaction
                                     (om/computed add-transaction
                                                  {:on-close     (utils/modal-on-close on-close-add-transaction)
                                                   :project-id   project-id
                                                   :project-name (:project/name active-project)}))
                         :on-close (utils/modal-on-close on-close-add-transaction)}))
         (when new-project?
           (let [on-close (utils/modal-on-close #(om/update-state! this assoc :new-project? false))]
             (utils/modal {:content  (->NewProject (om/computed
                                                     {}
                                                     {:on-close on-close
                                                      :on-save on-save-new-project}))
                           :on-close on-close})))]))))

(def ->NavbarSubmenu (om/factory NavbarSubmenu))

(defui NavbarMenu
  static om/IQuery
  (query [_]
    [{:proxy/add-transaction (om/get-query AddTransaction)}
     {:routing/navbar-settings {:settings (om/get-query Settings)
                                :nothing  []}}
     :query/navbar
     {:query/current-user [:user/uuid
                           :user/email]}])
  Object
  (initLocalState [this]
    {:menu-visible?                     false
     :new-transaction?                  false
     :add-widget?                       false
     :computed/add-transaction-on-close #(om/update-state! this assoc :new-transaction? false)})

  (render [this]
    (let [{:keys [proxy/add-transaction
                  query/current-user]
           :as props} (om/props this)
          {:keys [ui.component.navbar/settings-open?]} (:query/navbar props)
          settings-props (get-in props [:routing/navbar-settings])
          {:keys [menu-visible?
                  new-transaction?
                  computed/add-transaction-on-close]} (om/get-state this)]
      (html
        [:div.top-bar-container#topnav
         [:div.top-bar

          [:div.top-bar-left
           [:a.navbar-brand "jourmoney"]]

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
                      {:on-click #(do (om/update-state! this assoc :menu-visible? false)
                                      (om/transact! this `[(navbar/settings-show) :routing/navbar-settings]))}
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
            (utils/modal
              (let [on-close (utils/modal-on-close #(om/transact! this `[(navbar/settings-hide) :routing/app-root]))]
                {:content  (->Settings (om/computed settings-props {:on-close on-close}))
                 :on-close on-close})))
          (when new-transaction?
            (utils/modal {:content  (->AddTransaction
                                      (om/computed add-transaction
                                                   {:on-close (utils/modal-on-close add-transaction-on-close)}))
                          :on-close (utils/modal-on-close add-transaction-on-close)}))]]))))

(def ->NavbarMenu (om/factory NavbarMenu))

(defui Footer
  Object
  (render [_]
    (html
      [:div.footer
       [:ul.menu
        [:li
         [:small
          "Say hi to us anytime at "
          [:a.mail-link {:href "mailto:info@jourmoney.com"}
           "info@jourmoney.com"]]]]
       [:ul.menu
        [:li
         [:small
          [:a {:href  "//www.iubenda.com/privacy-policy/7944779"
               :class "iubenda-nostyle no-brand iubenda-embed"
               :title "Privacy Policy"}
           "Privacy Policy"]]]
        [:li
         [:small
          [:a {:href  "/terms"
               :target "_blank"}
           "Terms of Service"]]]
        [:li
         [:small
          "Copyright © eponai 2016. All Rights Reserved"]]]])))
(def ->Footer (om/factory Footer))
