(ns eponai.web.ui.project
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.routes :as routes]
    [eponai.web.ui.add-widget.add-goal :refer [->NewGoal NewGoal]]
    [eponai.web.ui.add-widget :refer [NewWidget ->NewWidget]]
    [eponai.web.ui.add-transaction :refer [->AddTransaction AddTransaction]]
    [eponai.web.ui.all-transactions :refer [->AllTransactions AllTransactions]]
    [eponai.web.ui.dashboard :refer [->Dashboard Dashboard]]
    [eponai.web.ui.utils :as utils]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug error]]
    [medley.core :as medley]))

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

(defui SubMenu
  static om/IQuery
  (query [this]
    (let [query [{:proxy/add-transaction (om/get-query AddTransaction)}
                 {:query/active-dashboard [:db/id {:widget/_dashboard [:widget/index]}]}]]
      (if (and (om/component? this)
               (some (set (keys (om/get-state this))) [:new-track? :new-goal?]))
        (conj query {:proxy/new-widget (om/get-query NewWidget)})
        (conj query {:proxy/new-widget (utils/query-with-component-meta NewWidget
                                                                        ;; What key we use here shouldn't really matter
                                                                        ;; but we're using a subset of NewWidget's
                                                                        ;; query because we're scared of what might happen
                                                                        ;; if we don't.
                                                                        [:query/tags])}))))
  Object
  (initLocalState [this]
    {:computed/share-project-on-save #(let [project (-> (om/get-computed this)
                                                        (get-in [:app-content :query/active-project :ui.component.project/active-project]))]
                                       (.share-project this (:project/uuid project) %))
     :computed/new-track-on-save     #(do (om/update-state! this assoc :new-track? false)
                                          (.set-query! this))
     :computed/new-goal-on-save      #(do (om/update-state! this assoc :new-goal? false)
                                          (.set-query! this))
     :computed/on-menu-select        #(om/update-state! this :menu-visible? true)})

  (set-query! [this]
    (om/update-query! this assoc :query (om/query this)))

  (share [this]
    (om/update-state! this assoc :share-project? true))

  (share-project [this project-uuid email]
    (om/transact! this `[(project/share ~{:project/uuid project-uuid
                                          :user/email email})]))

  (last-widget-index [_ widgets]
    (let [{:keys [widget/index]} (last (sort-by :widget/index widgets))]
      (or index 0)))

  (render [this]
    (let [{:keys [proxy/add-transaction
                  proxy/new-widget
                  query/active-dashboard]} (om/props this)
          {:keys [app-content]} (om/get-computed this)
          {:keys [query/active-project]} app-content
          project (:ui.component.project/active-project active-project)
          selected-tab (get-in app-content [:query/active-project :ui.component.project/selected-tab])

          {:keys [menu-visible?
                  new-transaction?
                  new-track?
                  new-goal?
                  share-project?
                  computed/share-project-on-save
                  computed/new-track-on-save
                  computed/new-goal-on-save]} (om/get-state this)
          {:keys [db/id]} project]
      ;(debug "App content..." active-dashboard)
      (html
        [:div#project-submenu.row.expanded.collapse

         [:div.menu-horizontal.columns.small-4
          [:div.nav-link.truncate
           [:small.truncate [:strong (:project/name project)]]]
          [:div.nav-link
           [:div
            [:a.button
             (opts {:on-click #(om/update-state! this assoc :menu-visible? true)
                    :style {:padding "0.5em"}})
             [:i.fa.fa-plus.fa-fw
              (opts {:style {:margin 0}})]
             [:span.small-caps "New... "]]
            (when menu-visible?
              (let [on-close #(om/update-state! this assoc :menu-visible? false)]
                [:div
                 (utils/click-outside-target on-close)
                 [:div.menu.dropdown
                  [:a.nav-link
                   {:on-click #(om/update-state! this assoc
                                                 :new-transaction? true
                                                 :menu-visible? false)}
                   [:i.fa.fa-money.fa-fw]
                   [:span.small-caps "Transaction"]]
                  [:a.nav-link
                   {:on-click #(do (om/update-state! this assoc
                                                     :new-track? true
                                                     :menu-visible? false)
                                   (.set-query! this))}
                   [:i.fa.fa-line-chart.fa-fw
                    (opts {:style {:color "green"}})]
                   [:span.small-caps "Track"]]
                  [:a.nav-link
                   {:on-click #(do (om/update-state! this assoc
                                                     :new-goal? true
                                                     :menu-visible? false)
                                   (.set-query! this))}
                   [:i.fa.fa-star.fa-fw
                    (opts {:style {:color "orange"}})]
                   [:span.small-caps "Goal"]]]])
              )]]]
         [:div.menu-horizontal.columns.small-4.align-center
          [:a.nav-link.tab
           {:class (when (= selected-tab :transactions) "selected")
            :href  (when id
                     (routes/key->route :route/project->txs {:route-param/project-id id}))}
           [:i.fa.fa-list.fa-fw.hidden-medium-up]
           [:span.small-caps.visible-medium-up "List"]]

          [:a.nav-link.tab
           {:class (when (contains? #{:dashboard :widget} selected-tab) "selected")
            :href (when id
                    (routes/key->route :route/project->dashboard
                                       {:route-param/project-id id}))}
           [:i.fa.fa-dashboard.fa-fw.hidden-medium-up]
           [:span.small-caps.visible-medium-up "Dashboard"]]]
         ;[:div.top-bar-right]
         [:div.menu-horizontal.columns.small-4.align-right
          [:a.nav-link
           [:i.fa.fa-user]
           [:small (count (:project/users project))]]
          (when-not utils/*playground?*
            [:a.nav-link
             {:on-click #(do (debug "Clicked share"
                                    (.share this)))}
             [:i.fa.fa-share-alt]])
          (when share-project?
            (let [on-close #(om/update-state! this assoc :share-project? false)]
              (utils/modal {:content (->Shareproject (om/computed {}
                                                                  {:on-close on-close
                                                                   :on-save share-project-on-save}))
                            :on-close on-close})))

          (when new-transaction?
            (let [on-close #(om/update-state! this assoc :new-transaction? false)]
              (utils/modal {:content  (->AddTransaction (om/computed add-transaction {:on-close on-close}))
                            :on-close on-close})))
          (when new-track?
            (utils/modal {:content  (->NewWidget (om/computed new-widget
                                                              {:dashboard-id (:db/id active-dashboard)
                                                               :widget-type :track
                                                               :index       (.last-widget-index this (:widget/_dashboard active-dashboard))
                                                               :on-save     new-track-on-save
                                                               :count (count (:widget/_dashboard active-dashboard))}))
                          :on-close new-track-on-save
                          :size     "large"}))
          (when new-goal?
            (utils/modal {:content  (->NewWidget (om/computed new-widget
                                                              {:dashboard-id (:db/id active-dashboard)
                                                               :widget-type  :goal
                                                               :index        (.last-widget-index this (:widget/_dashboard active-dashboard))
                                                               :on-save      new-goal-on-save
                                                               :count (count (:widget/_dashboard active-dashboard))}))
                          :on-close new-goal-on-save
                          :size     "medium"}))]]))))

(def ->SubMenu (om/factory SubMenu))

(def content->component {:dashboard    {:factory ->Dashboard :component Dashboard}
                         :transactions {:factory ->AllTransactions :component AllTransactions}
                         :widget       {:factory ->NewWidget :component NewWidget}
                         :goal         {:factory ->NewGoal :component NewGoal}})

(comment
  ;; Figure out if we still want to do this
  (componentWillUnmount [this]
                        (om/transact! this `[(ui.component.project/clear)
                                             :query/transactions])))

(defui Project
  static om/IQuery
  (query [this]
    [{:query/active-project [:ui.component.project/selected-tab
                             :ui.component.project/active-project]}
     {:routing/project (medley/map-vals #(-> % :component om/get-query) content->component)}])

  Object
  (render [this]
    (let [{:keys [query/active-project] :as props} (om/props this)
          {:keys [share-project?]} (om/get-state this)
          project (:ui.component.project/active-project active-project)]
      (debug "Project props: " props)
      ;; TODO: See what reads return.
      (html
        ;[:div]
        ;(when project
        ;  (nav/->NavbarSubmenu (om/computed {}
        ;                                    {:content (->SubMenu (om/computed {}
        ;                                                                      {:project project
        ;                                                                       :selected-tab (:ui.component.project/selected-tab active-project)}))})))
        [:div#project-content
         (let [content (:ui.component.project/selected-tab active-project)
               factory (-> content->component content :factory)
               props (:routing/project props)]
           (when factory
             (factory props)))]

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
