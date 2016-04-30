(ns eponai.web.ui.root
  (:require [om.next :as om :refer-macros [defui]]
            [om.next.protocols :as om.p]
            [sablono.core :refer-macros [html]]
            [eponai.web.ui.navigation :as nav]
            [eponai.web.ui.project :refer [Project ->Project]]
            [eponai.web.ui.utils :as u]
            [eponai.common.parser :as parser]
            [taoensso.timbre :refer-macros [debug error warn]]))

(defn get-app-content [props k]
  (get-in props [:query/root-component :ui.component.root/app-content k]))

(defn props->main-component [props]
  (get-app-content props :component))

(defn props->main-factory [props]
  (get-app-content props :factory))

(defn component->dynamic-query [this c]
  (let [subquery (or (om/subquery this (u/component->ref c) c)
                     (om/get-query c))
        query {:proxy/app-content [{(u/component->query-key c) subquery}]}]
    query))

(defui ^:once App
  static om/IQuery
  (query [this]
    (cond-> [:datascript/schema
             :user/current
             {:query/root-component [:ui.component.root/app-content
                                     :ui.component.root/route-changed]}
             {:proxy/nav-bar (om/get-query nav/NavbarMenu)}
             {:proxy/nav-bar-submenu (om/get-query nav/NavbarSubmenu)}
             {:proxy/side-bar (om/get-query nav/SideBar)}]
            (and (om/component? this) (props->main-component (om/props this)))
            (conj (component->dynamic-query this (props->main-component (om/props this))))))

  u/IDynamicQuery
  (update-query! [this]
    (when-let [component (om/react-ref this (u/component->ref (props->main-component (om/props this))))]
      (when (satisfies? u/IDynamicQuery component)
        (u/update-query! component)))
    (let [q (om/query this)]
      (om/set-query! this {:query q})))

  Object
  (initLocalState [_]
    {:sidebar-visible? false})

  (componentDidUpdate [this _ _]
    (let [props (om/props this)
          component (props->main-component props)]
      (when (get-in props [:query/root-component :ui.component.root/route-changed])
        (u/update-query! this)
        (let [dynamic-q (component->dynamic-query this component)]
          (om/transact! (om/get-reconciler this) `[(root/ack-route-changed) ~dynamic-q])))))

  (render
    [this]
    (let [{:keys [proxy/nav-bar-submenu
                  proxy/nav-bar
                  proxy/side-bar] :as props} (om/props this)
          {:keys [sidebar-visible?]} (om/get-state this)
          factory (props->main-factory props)
          component (props->main-component props)
          child-props (get-in props [:proxy/app-content (u/component->query-key component)])]
      (html
        [:div#jourmoney-ui
         ;[:div#wrapper]
         (nav/->SideBar (om/computed side-bar
                                     {:expanded? sidebar-visible?
                                      :on-close  (when sidebar-visible?
                                                   #(om/update-state! this assoc :sidebar-visible? false))}))

         [:div#main-page
          (nav/->NavbarMenu (om/computed nav-bar
                                         {:on-sidebar-toggle #(om/update-state! this update :sidebar-visible? not)}))
          (nav/->NavbarSubmenu (om/computed nav-bar-submenu
                                            {:content-factory factory
                                             :app-content     child-props}))
          [:div#page-content
           (when factory
             (factory (assoc child-props :ref (u/component->ref component))))]]]))))