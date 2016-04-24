(ns eponai.web.ui.project
  (:require
    [eponai.web.ui.add-widget.add-goal :refer [->NewGoal NewGoal]]
    [eponai.web.ui.add-widget :refer [NewWidget ->NewWidget]]
    [eponai.web.ui.all-transactions :refer [->AllTransactions AllTransactions]]
    [eponai.web.ui.dashboard :as dashboard :refer [->Dashboard Dashboard]]
    [eponai.web.ui.navigation :as nav]
    [eponai.web.ui.utils :as utils]
    [eponai.client.ui :refer-macros [opts]]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug error]]
    [eponai.web.routes :as routes]
    [datascript.core :as d]))

(defui SubMenu
  Object
  (share [this]
    (om/update-state! this assoc :share-project? true))

  (share-project [this project-uuid email]
    (om/transact! this `[(project/share ~{:project/uuid project-uuid
                                          :user/email email})]))
  (render [this]
    (let [{:keys [project selected-tab]} (om/get-computed this)
          {:keys [menu-visible?]} (om/get-state this)
          {:keys [db/id]} project
          on-close #(om/update-state! this assoc :menu-visible? false)]
      (html
        [:div
         [:ul.dropdown.menu.float-left
          [:li
           [:a
            {:href (routes/key->route :route/project->txs {:route-param/project-id id})}
            [:i.fa.fa-list]]]
          [:li
           [:a
            {:href (routes/key->route :route/project->dashboard
                                      {:route-param/project-id id})}
            [:strong "Dashboard"]]]



          [:li.has-submenu.opens-right
           [:a
            {:on-click #(om/update-state! this assoc :menu-visible? true)}
            "New..."]

           (when menu-visible?
             [:div
              ;(opts {:style {:position :absolute
              ;               :right 0}})
              ;{:class "dropdown open"}
              (utils/click-outside-target on-close)
              [:ul.dropdown-menu
               (opts {:style {:left 0}})
               [:li
                [:a
                 (opts {:href     (routes/key->route :route/project->widget+type+id {:route-param/project-id  id
                                                                                     :route-param/widget-type :track
                                                                                     :route-param/widget-id   "new"})
                        :on-click on-close
                        :style    {:padding "0.5em"}})
                 [:i.fa.fa-line-chart.fa-fw
                  (opts {:style {:width "15%"
                                 :color "green"}})]
                 [:strong "Track"]]]
               [:li
                [:a
                 (opts {:href     (routes/key->route :route/project->widget+type+id {:route-param/project-id  id
                                                                                     :route-param/widget-type :goal
                                                                                     :route-param/widget-id   "new"})
                        :on-click on-close
                        :style {:padding "0.5em"}})
                 [:i.fa.fa-star.fa-fw
                  (opts {:style {:width "15%"
                                 :color "orange"}})]
                 [:strong "Goal"]]]]])]
          ]
         ;[:div.top-bar-right]
         [:ul.menu.float-right
          [:li
           [:a.disabled
            [:i.fa.fa-user]
            [:small (count (:project/users project))]]]
          [:li
           [:a
            {:on-click #(.share this)}
            [:i.fa.fa-share-alt]]]
          ]]))))
(def ->SubMenu (om/factory SubMenu))

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
        [:div
         (when project
           (nav/->NavbarSubmenu (om/computed {}
                                             {:content (->SubMenu (om/computed {}
                                                                               {:project project
                                                                                :selected-tab (:ui.component.project/selected-tab active-project)}))})))
         [:div#project-content
          (let [content (project-content this)
                factory (get-in content->component [content :factory])
                props (cond-> (assoc child-content :ref content)
                              (= :widget content)
                              (om/computed {:dashboard (:query/dashboard dashboard)
                                            :index (dashboard/calculate-last-index 4 (:widget/_dashboard dashboard))}))]
            (factory props))]

         (when share-project?
           (let [on-close #(om/update-state! this assoc :share-project? false)]
             (utils/modal {:content (->Shareproject (om/computed {}
                                                                {:on-close on-close
                                                                 :on-save #(.share-project this (:project/uuid project) %)}))
                           :on-close on-close})))]))))

(def ->Project (om/factory Project))
