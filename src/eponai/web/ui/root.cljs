(ns eponai.web.ui.root
  (:require [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [eponai.web.ui.navigation :as nav]
            [eponai.web.ui.project :refer [Project]]
            [eponai.web.ui.utils :as u]
            [eponai.web.routes.ui-handlers :as routes]
            [medley.core :as medley]
            [taoensso.timbre :refer-macros [debug error warn]]))

(defui ^:once App
  static om/IQuery
  (query [this]
    [:datascript/schema
     :user/current
     {:query/root-component [:ui.component.root/route-handler]}
     {:proxy/nav-bar (om/get-query nav/NavbarMenu)}
     {:proxy/nav-bar-submenu (or (om/subquery this :nav-bar-submenu nav/NavbarSubmenu)
                                 (om/get-query nav/NavbarSubmenu))}
     {:proxy/side-bar (om/get-query nav/SideBar)}
     {:routing/app-root (medley/map-vals #(-> % :component om/get-query)
                                            routes/route-key->root-handler)}])

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
                  routing/app-content] :as props} (om/props this)
          {:keys [sidebar-visible?
                  computed/side-bar-on-close
                  computed/navbar-menu-on-sidebar-toggle]} (om/get-state this)
          factory (-> props
                      :query/root-component
                      :ui.component.root/route-handler
                      :route-key
                      routes/route-key->root-handler
                      :factory)]
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
             (factory app-content))]]]))))