(ns eponai.web.ui.project
  (:require [eponai.web.ui.all-transactions :refer [->AllTransactions AllTransactions]]
            [eponai.web.ui.dashboard :refer [->Dashboard Dashboard]]
            [eponai.web.ui.navigation :as nav]
            [eponai.web.ui.utils :as utils]
            [eponai.client.ui :refer-macros [opts]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]
            [eponai.web.routes :as routes]))

(defn- submenu [component project]
  (html
    [:ul.menu
     [:li
      [:a.disabled
       [:i.fa.fa-user]
       [:small (count (:project/users project))]]]
     [:li
      [:a
       {:on-click #(.share component)}
       [:i.fa.fa-share-alt]]]
     [:li
      [:a
       {:href (routes/key->route :route/project->dashboard
                                 {:route-param/project-id (:db/id project)})}
       [:span "Dashboard"]]]
     [:li
      [:a
       {:href (routes/key->route :route/project->txs {:route-param/project-id (:db/id project)})}
       [:span "Transactions"]]]]))

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

(defui Project
  static om/IQuery
  (query [_]
    [{:query/active-project [:ui.component.project/selected-tab]}
     {:proxy/dashboard (om/get-query Dashboard)}
     {:proxy/all-transactions (om/get-query AllTransactions)}])
  Object
  (share [this]
    (om/update-state! this assoc :share-project? true))

  (share-project [this project-uuid email]
    (om/transact! this `[(project/share ~{:project/uuid project-uuid
                                          :user/email email})]))
  (componentWillUnmount [this]
    (om/transact! this `[(ui.component.project/clear)
                         :query/transactions]))

  (render [this]
    (let [{:keys [query/active-project proxy/dashboard
                  proxy/all-transactions]} (om/props this)
          {:keys [share-project?]} (om/get-state this)
          project (get-in dashboard [:query/dashboard :dashboard/project])]
      (html
        [:div
         (nav/->NavbarSubmenu (om/computed {}
                                           {:content (when project
                                                       (submenu this project))}))
         [:div#project-content
          (condp = (or (:ui.component.project/selected-tab active-project)
                       :dashboard)
            :dashboard (->Dashboard dashboard)
            :transactions (->AllTransactions all-transactions))]

         (when share-project?
           (let [on-close #(om/update-state! this assoc :share-project? false)]
             (utils/modal {:content (->Shareproject (om/computed {}
                                                                {:on-close on-close
                                                                 :on-save #(.share-project this (:project/uuid project) %)}))
                           :on-close on-close})))]))))

(def ->Project (om/factory Project))
