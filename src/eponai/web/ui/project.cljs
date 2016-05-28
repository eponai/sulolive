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

(defui SubMenu
  static om/IQuery
  (query [_]
    [{:proxy/add-transaction (om/get-query AddTransaction)}
     {:proxy/new-widget (om/get-query NewWidget)}
     {:query/dashboard [:db/id
                        :widget/_dashboard]}])
  Object
  (initLocalState [this]
    {:computed/share-project-on-save #(let [project (-> (om/get-computed this)
                                                        (get-in [:app-content :query/active-project :ui.component.project/active-project]))]
                                       (.share-project this (:project/uuid project) %))
     :computed/new-track-on-save #(om/update-state! this assoc :new-track? false)
     :computed/new-goal-on-save #(om/update-state! this assoc :new-goal? false)
     :computed/on-menu-select #(om/update-state! this :menu-visible? true)})

  (share [this]
    (om/update-state! this assoc :share-project? true))

  (share-project [this project-uuid email]
    (om/transact! this `[(project/share ~{:project/uuid project-uuid
                                          :user/email email})]))
  (render [this]
    (let [{:keys [proxy/add-transaction
                  proxy/new-widget
                  query/dashboard]} (om/props this)
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
      ;(debug "App content..." dashboard)
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
                   {:on-click #(om/update-state! this assoc
                                                 :new-track? true
                                                 :menu-visible? false)}
                   [:i.fa.fa-line-chart.fa-fw
                    (opts {:style {:color "green"}})]
                   [:span.small-caps "Track"]]
                  [:a.nav-link
                   {:on-click #(om/update-state! this assoc
                                                 :new-goal? true
                                                 :menu-visible? false)}
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
                                                              {:dashboard-id (:db/id dashboard)
                                                               :widget-type :track
                                                               :index       (dashboard/calculate-last-index (:widget/_dashboard dashboard))
                                                               :on-save     new-track-on-save}))
                          :on-close new-track-on-save
                          :size     "large"}))
          (when new-goal?
            (utils/modal {:content  (->NewWidget (om/computed new-widget
                                                              {:dashboard-id (:db/id dashboard)
                                                               :widget-type  :goal
                                                               :index        (dashboard/calculate-last-index (:widget/_dashboard dashboard))
                                                               :on-save      new-goal-on-save}))
                          :on-close new-goal-on-save
                          :size     "medium"}))]]))))

(def ->SubMenu (om/factory SubMenu))

(def content->component {:dashboard    {:factory ->Dashboard :component Dashboard}
                         :transactions {:factory ->AllTransactions :component AllTransactions}
                         :widget       {:factory ->NewWidget :component NewWidget}
                         :goal         {:factory ->NewGoal :component NewGoal}})

(defn project-content [x]
  (let [props (if (om/component? x) (om/props x) x)]
    (get-in props [:query/active-project :ui.component.project/selected-tab])))

(defn content->query [this content]
  (let [component (when content (get-in content->component [content :component]))]
    (cond-> [{:query/active-project [:ui.component.project/selected-tab
                                     :ui.component.project/active-project]}]
            (some? component)
            (conj {:proxy/child-content (or (om/subquery this content component) (om/get-query component))})
            (= :widget content)
            (conj {:proxy/dashboard (om/get-query Dashboard)}))))

(comment
  ;; Figure out if we still want to do this
  (componentWillUnmount [this]
                        (om/transact! this `[(ui.component.project/clear)
                                             :query/transactions])))

(defui Project
  static om/IQuery
  (query [this]
    (let [content (project-content this)]
      (content->query this content)))

  static utils/IDynamicQuery
  (dynamic-query-fragment [_]
    [{:query/active-project [:ui.component.project/selected-tab]}])
  (next-query [this next-props]
    (let [content (project-content next-props)]
      {:query (content->query this content)}))


  Object

  (render [this]
    (let [{:keys [query/active-project
                  proxy/child-content
                  proxy/dashboard]} (om/props this)
          {:keys [share-project?]} (om/get-state this)
          project (:ui.component.project/active-project active-project)
          ]
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
                                           :index     (dashboard/calculate-last-index (:widget/_dashboard dashboard))}))]
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
