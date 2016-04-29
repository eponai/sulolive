(ns eponai.web.ui.project
  (:require
    [eponai.web.ui.add-widget.add-goal :refer [->NewGoal NewGoal]]
    [eponai.web.ui.add-widget :refer [NewWidget ->NewWidget]]
    [eponai.web.ui.add-transaction :refer [->AddTransaction AddTransaction]]
    [eponai.web.ui.all-transactions :refer [->AllTransactions AllTransactions]]
    [eponai.web.ui.dashboard :as dashboard :refer [->Dashboard Dashboard]]
    ;[eponai.web.ui.navigation :as nav]
    [eponai.web.ui.utils :as utils]
    [eponai.client.ui :refer-macros [opts]]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug error]]
    [eponai.web.routes :as routes]
    [datascript.core :as d]))

(defui Shareproject
  Object
  (render [this]
    (let [{:keys [on-close on-save]} (om/get-computed this)
          {:keys [input-email]} (om/get-state this)]
      (html
        [:div.clearfix
         [:h3 "Share"]
         [:label "Invite a friend to collaborate on this project:"]
         [:input
          {:type        "email"
           :value       (or input-email "")
           :on-change   #(om/update-state! this assoc :input-email (.. % -target -value))
           :placeholder "yourfriend@example.com"}]

         [:div.float-right
          [:a.button.secondary
           {:on-click on-close}
           "Cancel"]
          [:a.button.primary
           {:on-click #(do (on-save input-email)
                           (on-close))}
           "Share"]]]))))

(def ->Shareproject (om/factory Shareproject))

(def content->component {:dashboard    {:factory ->Dashboard :component Dashboard}
                         :transactions {:factory ->AllTransactions :component AllTransactions}
                         :widget       {:factory ->NewWidget :component NewWidget}
                         :goal         {:factory ->NewGoal :component NewGoal}})


(defn default-content []
  (let [r (deref utils/reconciler-atom)
        db-content (when r
                     (d/q '{:find  [?tab .]
                            :where [[?e :ui/component :ui.component/project]
                                    [?e :ui.component.project/selected-tab ?tab]]}
                          (d/db (om/app-state r))))]
    (debug "db-content: " db-content)
    (or db-content)))

(defn project-content [component]
  (default-content))

(defui SubMenu
  static om/IQuery
  (query [_]
    [{:proxy/add-transaction (om/get-query AddTransaction)}])
  Object
  (share [this]
    (om/update-state! this assoc :share-project? true))

  (share-project [this project-uuid email]
    (om/transact! this `[(project/share ~{:project/uuid project-uuid
                                          :user/email email})]))
  (render [this]
    (let [{:keys [proxy/add-transaction]} (om/props this)
          {:keys [app-content]} (om/get-computed this)
          {:keys [query/active-project]} app-content
          project (:ui.component.project/active-project active-project)
          selected-tab (get-in app-content [:query/active-project :ui.component.project/selected-tab])

          {:keys [menu-visible?
                  new-transaction?
                  share-project?]} (om/get-state this)
          {:keys [db/id]} project
          on-close #(om/update-state! this assoc :menu-visible? false)]
      (html
        [:div#project-submenu.row.expanded
         (when new-transaction?
           (let [on-close #(om/update-state! this assoc :new-transaction? false)]
             (utils/modal {:content  (->AddTransaction
                                       (om/computed add-transaction
                                                    {:on-close on-close}))
                           :on-close on-close})))
         [:div.menu-horizontal.columns.small-4
          [:div.nav-link
           [:small [:strong (:project/name project)]]]
          [:div.nav-link
           [:a
            {:on-click #(om/update-state! this assoc :menu-visible? true)}
            [:strong.small-caps "New..."]]

           (when menu-visible?
             [:div
              (utils/click-outside-target on-close)
              [:div.menu.dropdown
               [:a.nav-link
                {:on-click #(om/update-state! this assoc :new-transaction? true :menu-visible? false)}
                [:i.fa.fa-money.fa-fw
                 (opts {:style {:color "black"}})]
                [:span.small-caps "Transaction"]]
               [:a.nav-link
                (opts {:href (when id (routes/key->route :route/project->widget+type+id {:route-param/project-id  id
                                                                                         :route-param/widget-type :track
                                                                                         :route-param/widget-id   "new"}))
                       :on-click on-close
                       :style    {:padding "0.5em"}})
                [:i.fa.fa-line-chart.fa-fw
                 (opts {:style {:color "green"}})]
                [:span.small-caps "Track"]]
               [:a.nav-link
                (opts {:href     (when id
                                   (routes/key->route :route/project->widget+type+id {:route-param/project-id  id
                                                                                      :route-param/widget-type :goal
                                                                                      :route-param/widget-id   "new"}))
                       :on-click on-close
                       :style    {:padding "0.5em"}})
                [:i.fa.fa-star.fa-fw
                 (opts {:style {:color "orange"}})]
                [:span.small-caps "Goal"]]]])]]
         [:div.menu-horizontal.columns.small-4.align-center
          [:a.nav-link.tab
           {:class (when (= selected-tab :transactions) "selected")
            :href  (when id
                     (routes/key->route :route/project->txs {:route-param/project-id id}))}
           [:span.small-caps "List"]]

          [:a.nav-link.tab
           {:class (when (= selected-tab :dashboard) "selected")
            :href (when id
                    (routes/key->route :route/project->dashboard
                                       {:route-param/project-id id}))}
           [:span.small-caps "Dashboard"]]]
         ;[:div.top-bar-right]
         [:div.menu-horizontal.columns.small-4.align-right
          [:a.nav-link
           [:i.fa.fa-user]
           [:small (count (:project/users project))]]
          [:a.nav-link
           {:on-click #(do (debug "Clicked share"
                                  (.share this)))}
           [:i.fa.fa-share-alt]]
          (when share-project?
            (let [on-close #(om/update-state! this assoc :share-project? false)]
              (utils/modal {:content (->Shareproject (om/computed {}
                                                                  {:on-close on-close
                                                                   :on-save #(.share-project this (:project/uuid project) %)}))
                            :on-close on-close})))
          ]]))))

(def ->SubMenu (om/factory SubMenu))

(defui Project
  static om/IQuery
  (query [this]
    (let [content (project-content this)
          component (get-in content->component [content :component])
          query (om/subquery this content component)]
      (cond-> [{:query/active-project [:ui.component.project/selected-tab
                                       :ui.component.project/active-project]}]
              (some? query)
              (conj {:proxy/child-content query})
              (= :widget content)
              (conj {:proxy/dashboard (om/get-query Dashboard)}))))
  Object
  (componentWillUnmount [this]
    (om/transact! this `[(ui.component.project/clear)
                         :query/transactions]))

  (render [this]
    (let [{:keys [query/active-project
                  proxy/child-content
                  proxy/dashboard]} (om/props this)
          {:keys [share-project?]} (om/get-state this)
          project (:ui.component.project/active-project active-project)]
      (html
        ;[:div]
        ;(when project
        ;  (nav/->NavbarSubmenu (om/computed {}
        ;                                    {:content (->SubMenu (om/computed {}
        ;                                                                      {:project project
        ;                                                                       :selected-tab (:ui.component.project/selected-tab active-project)}))})))
        [:div#project-content
         (let [content (project-content this)
               factory (get-in content->component [content :factory])
               props (cond-> (assoc child-content :ref content)
                             (= :widget content)
                             (om/computed {:dashboard (:query/dashboard dashboard)
                                           :index     (dashboard/calculate-last-index 4 (:widget/_dashboard dashboard))}))]
           (factory props))]

        ;(when share-project?
        ;  (let [on-close #(om/update-state! this assoc :share-project? false)]
        ;    (utils/modal {:content (->Shareproject (om/computed {}
        ;                                                       {:on-close on-close
        ;                                                        :on-save #(.share-project this (:project/uuid project) %)}))
        ;                  :on-close on-close})))
        ))))
;=======
;                  proxy/dashboard
;                  proxy/add-transaction
;                  proxy/all-transactions
;                  proxy/new-widget]} (om/props this)
;          project (get-in dashboard [:query/dashboard :dashboard/project])]
;      (html
;        ;[:div#project]
;        ;(when project
;        ;  (->SubMenu (om/computed add-transaction
;        ;                          {:project project
;        ;                           :selected-tab (:ui.component.project/selected-tab active-project)})))
;        [:div#project-content
;         (condp = (or (:ui.component.project/selected-tab active-project)
;                      :dashboard)
;           :dashboard (->Dashboard dashboard)
;           :transactions (->AllTransactions all-transactions)
;           :widget (->NewWidget (om/computed new-widget
;                                             {:dashboard (:query/dashboard dashboard)
;                                              :index     (dashboard/calculate-last-index 4 (:widget/_dashboard dashboard))})))]))))
;>>>>>>> Start cleaning upp CSS, and change our element structure.

(def ->Project (om/factory Project))
