(ns eponai.web.ui.root
  (:require [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [eponai.web.ui.navigation :as nav]
            [eponai.web.ui.project :refer [Project ->Project]]
            [eponai.web.ui.utils :as u]
            [taoensso.timbre :refer-macros [debug error warn]]))

(defn get-in-app-content [props k]
  (get-in props [:query/root-component :ui.component.root/app-content k]))

(defn props->component [props]
  (get-in-app-content props :component))

(defn props->factory [props]
  (get-in-app-content props :factory))


(defn component->query [this c]
  (cond-> [:datascript/schema
           :user/current
           {:query/root-component [:ui.component.root/app-content :ui.component.root/route-changed]}
           {:proxy/nav-bar (om/get-query nav/NavbarMenu)}
           {:proxy/nav-bar-submenu (or (om/subquery this :nav-bar-submenu nav/NavbarSubmenu)
                                       (om/get-query nav/NavbarSubmenu))}
           {:proxy/side-bar (om/get-query nav/SideBar)}]
          (and (om/component? this) (some? c))
          (conj {:proxy/app-content '?app-content})))

(defui ^:once App
  static om/IQueryParams
  (params [this]
    {:app-content (when (om/component? this)
                    (let [c (props->component (om/props this))]
                      (or (when (some? c) (om/subquery this :app-content c))
                          (om/get-query c))))})

  static om/IQuery
  (query [this]
    (component->query this (when (om/component? this) (props->component (om/props this)))))

  static u/IDynamicQuery
  (dynamic-query-fragment [_]
    [{:query/root-component [:ui.component.root/app-content]}])
  (next-query [this next-props]
    (let [c (props->component next-props)]
      {:query  (component->query this c)
       ;; This will get updated with DynamicQueryParams later.
       ;; Setting it handles the case where the path is not right?
       :params {:app-content (om/get-query c)}}))

  static u/IDynamicQueryParams
  (dynamic-params [this next-props]
    (let [component (props->component next-props)]
      {:app-content (or (when (= component (props->component (om/props this)))
                          (om/react-ref this :app-content))
                        component)}))

  Object
  (initLocalState [this]
    {:sidebar-visible?           false
     :computed/side-bar-on-close #(when (:sidebar-visible? (om/get-state this))
                                   (om/update-state! this assoc :sidebar-visible? false))
     :computed/navbar-menu-on-sidebar-toggle #(om/update-state! this update :sidebar-visible? not)})

  (render
    [this]
    (let [{:keys [proxy/nav-bar-submenu
                  proxy/nav-bar
                  proxy/side-bar
                  proxy/app-content] :as props} (om/props this)
          {:keys [sidebar-visible?
                  computed/side-bar-on-close
                  computed/navbar-menu-on-sidebar-toggle]} (om/get-state this)
          factory (props->factory props)]
      (html
        [:div#jourmoney-ui
         ;[:div#wrapper]
         (nav/->SideBar (om/computed side-bar
                                     {:expanded? sidebar-visible?
                                      :on-close  side-bar-on-close}))

         [:div#main-page
          (nav/->NavbarMenu (om/computed nav-bar {:on-sidebar-toggle navbar-menu-on-sidebar-toggle}))
          (nav/->NavbarSubmenu (om/computed (assoc nav-bar-submenu :ref :nav-bar-submenu)
                                            {:content-factory factory
                                             :app-content     app-content}))
          [:div#page-content
           (when factory
             (factory (assoc app-content :ref :app-content)))]]]))))