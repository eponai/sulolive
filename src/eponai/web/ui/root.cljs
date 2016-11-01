(ns eponai.web.ui.root
  (:require
    [eponai.web.ui.navigation :as nav]
    [eponai.web.routes.ui-handlers :as routes]
    [eponai.web.ui.utils :as utils]
    [medley.core :as medley]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
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
     {:routing/app-root (medley/map-vals #(->> % :component om/get-query)
                                            routes/route-key->root-handler)}])

  Object
  (render
    [this]
    (let [{:keys [proxy/nav-bar-submenu
                  proxy/nav-bar
                  routing/app-root] :as props} (om/props this)
          factory (-> props
                      :query/root-component
                      :ui.component.root/route-handler
                      :route-key
                      routes/route-key->root-handler
                      :factory)]
      (html
        [:div#jm-ui
         [:div#nav-container
          (nav/->NavbarMenu nav-bar)
          (nav/->NavbarSubmenu (om/computed (assoc nav-bar-submenu :ref :nav-bar-submenu)
                                            {:content-factory factory
                                             :app-content     app-root}))
          (when utils/*playground?*
            [:div.callout.small.primary.text-center
             "Welcome to the playground, where you can check out the full app. "
             [:strong
              [:a
               {:on-click #(om/update-state! this assoc :playground/show-subscribe-modal? true)}
               "Sign up"]]
             " to save your progress."])
          (utils/subscribe-modal this)]
         [:div#page-content
          {:ref (str ::page-content-ref)}
          (when factory
            (factory app-root))]
         [:div#footer-container
          (nav/->Footer)]]))))