(ns eponai.web.ui.root
  (:require [om.next :as om :refer-macros [defui]]
            [om.next.protocols :as om.p]
            [sablono.core :refer-macros [html]]
            [eponai.web.ui.navigation :as nav]
            [eponai.web.ui.project :refer [Project ->Project]]
            [eponai.web.ui.utils :as u]
            [eponai.common.parser :as parser]
            [taoensso.timbre :refer-macros [debug error warn]]))

(comment
  (componentDidUpdate [this _ _]
                      (let [props (om/props this)
                            component (props->main-component props)]
                        (when (get-in props [:query/root-component :ui.component.root/route-changed])
                          (u/update-query! this)
                          (let [dynamic-q (component->dynamic-query this component)]
                            (om/transact! (om/get-reconciler this) `[(root/ack-route-changed) ~dynamic-q]))))))

(defn get-app-content [props k]
  (get-in props [:query/root-component :ui.component.root/app-content k]))

(defn props->main-component [props]
  (get-app-content props :component))

(defn props->main-factory [props]
  (get-app-content props :factory))


(defn component->query [this c]
  (cond-> [:datascript/schema
           :user/current
           {:query/root-component [:ui.component.root/app-content :ui.component.root/route-changed]}
           {:proxy/nav-bar (om/get-query nav/NavbarMenu)}
           {:proxy/nav-bar-submenu (om/get-query nav/NavbarSubmenu)}
           {:proxy/side-bar (om/get-query nav/SideBar)}]
          (and (om/component? this) (some? c))
          (conj {:proxy/app-content '?app-content})))

(defui ^:once App
  static om/IQueryParams
  (params [this]
    {:app-content (when (om/component? this)
                    (let [c (props->main-component (om/props this))]
                      (or (when (some? c) (om/subquery this :app-content c))
                          (om/get-query c))))})

  static om/IQuery
  (query [this]
    (component->query this (when (om/component? this) (props->main-component (om/props this)))))

  u/IDynamicQuery
  (dynamic-query [_]
    [{:query/root-component [:ui.component.root/app-content]}])
  (next-query [this next-props]
    (let [c (props->main-component next-props)]
      {:query  (component->query this c)
       ;; This will get updated with DynamicQueryParams later.
       ;; Setting it handles the case where the path is not right?
       :params {:app-content (om/get-query c)}}))

  u/IDynamicQueryParams
  (dynamic-params [this next-props]
    (let [component (props->main-component next-props)]
      {:app-content (or (when (= component (props->main-component (om/props this)))
                          (om/react-ref this :app-content))
                        component)}))

  Object
  (initLocalState [_]
    {:sidebar-visible? false})

  (render
    [this]
    (let [{:keys [proxy/nav-bar-submenu
                  proxy/nav-bar
                  proxy/side-bar
                  proxy/app-content] :as props} (om/props this)
          {:keys [sidebar-visible?]} (om/get-state this)
          factory (props->main-factory props)]
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
                                             :app-content     app-content}))
          [:div#page-content
           (when factory
             (factory (assoc app-content :ref :app-content)))]]]))))