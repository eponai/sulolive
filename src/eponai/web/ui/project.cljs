(ns eponai.web.ui.project
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.routes :as routes]
    [eponai.web.ui.project.add-project :refer [->NewProject]]
    [eponai.web.ui.project.add-transaction :refer [->AddTransaction AddTransaction]]
    [eponai.web.ui.project.all-transactions :refer [->AllTransactions AllTransactions]]
    [eponai.web.ui.project.settings :refer [->ProjectSettings ProjectSettings]]
    [eponai.web.ui.project.dashboard :refer [->Dashboard Dashboard]]
    [eponai.web.ui.utils :as utils]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug error]]
    [medley.core :as medley]
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
                         :settings     {:factory ->ProjectSettings :component ProjectSettings}})

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
     {:routing/project (medley/map-vals #(->> % :component om/get-query) content->component)}])

  Object
  (initLocalState [this]
    {:on-save-new-project #(.save-new-project this %)})
  (save-new-project [this name]
    (om/transact! this [(list 'project/save {:project/uuid   (d/squuid)
                                             :project/name   name
                                             :dashboard/uuid (d/squuid)})
                        :query/all-projects]))
  (render [this]
    (let [{:keys [query/active-project] :as props} (om/props this)
          {:keys [add-project? on-save-new-project]} (om/get-state this)
          project (:ui.component.project/active-project active-project)]
      ;; TODO: See what reads return.
      (html
        ;[:div]
        ;(when project
        ;  (nav/->NavbarSubmenu (om/computed {}
        ;                                    {:content (->SubMenu (om/computed {}
        ;                                                                      {:project project
        ;                                                                       :selected-tab (:ui.component.project/selected-tab active-project)}))})))
        [:div
         [:div#project-content
          (if (some? (:ui.component.project/active-project active-project))
            (let [content (:ui.component.project/selected-tab active-project)
                  factory (-> content->component content :factory)
                  props (:routing/project props)]
              (when factory
                (factory (om/computed props
                                      {:project project}))))
            [:div.content-section
             ;[:a.button
             ; (opts {:style {:visibility :hidden}})
             ; "Button"]
             [:div.empty-message.text-center
              ;[:i.fa.fa-usd.fa-4x]
              ;[:i.fa.fa-eur.fa-4x]
              ;[:i.fa.fa-yen.fa-4x]
              [:i.fa.fa-th-list.fa-5x]
              [:div.lead
               "Looks like you don't have any projects yet."
               [:br]
               [:br]
               "Go ahead and "
               [:a.link
                {:on-click #(om/update-state! this assoc :add-project? true)}
                "create a new project"]
               "."]]])]
         (when add-project?
           (let [on-close #(om/update-state! this assoc :add-project? false)]
             (utils/modal {:content  (->NewProject (om/computed
                                                     {}
                                                     {:on-close on-close
                                                      :on-save  on-save-new-project}))
                           :on-close on-close})))]

        ;(when share-project?
        ;  (let [on-close #(om/update-state! this assoc :share-project? false)]
        ;    (utils/modal {:content (->Shareproject (om/computed {}
        ;                                                       {:on-close on-close
        ;                                                        :on-save #(.share-project this (:project/uuid project) %)}))
        ;                  :on-close on-close})))
        ))))

(def ->Project (om/factory Project))
