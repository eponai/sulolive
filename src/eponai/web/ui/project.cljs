(ns eponai.web.ui.project
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.routes :as routes]
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

(defui Settings
  static om/IQuery
  (query [_]
    [{:query/active-project [:ui.component.project/active-project]}])
  Object
  (render [this]
    (html "Project settings")))
(def ->Settings)

(def content->component {:dashboard    {:factory ->Dashboard :component Dashboard}
                         :transactions {:factory ->AllTransactions :component AllTransactions}
                         :settings     {:factory ->Settings :component Settings}})

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
  (render [this]
    (let [{:keys [query/active-project] :as props} (om/props this)
          {:keys [share-project?]} (om/get-state this)
          project (:ui.component.project/active-project active-project)]
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

(def ->Project (om/factory Project))
