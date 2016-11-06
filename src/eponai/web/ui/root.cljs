(ns eponai.web.ui.root
  (:require
    [eponai.web.ui.navigation :as nav]
    [eponai.web.routes.ui-handlers :as routes]
    [eponai.web.ui.utils :as utils]
    [eponai.web.ui.utils.button :as button]
    [eponai.web.ui.utils.css-classes :as css]
    [goog.format.EmailAddress]
    [medley.core :as medley]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug error warn]]))

(defn subscribe-modal [component]
  (when (:playground/show-subscribe-modal? (om/get-state component))
    (utils/modal
      {:on-close #(om/update-state! component assoc :playground/show-subscribe-modal? false)
       :content  (html
                   [:div#subscribe
                    [:h4.header "Coming soon"]
                    [:div.content
                     [:div.content-section.clearfix
                      [:p "We're working hard to make this available to you as soon as possible. In the meantime, subscribe to our newsletter to be notified when we launch."]
                      [:div.subscribe-input
                       [:input
                        {:value       (or (:playground/modal-input (om/get-state component)) "")
                         :type        "email"
                         :placeholder "youremail@example.com"
                         :on-change   #(om/update-state! component assoc :playground/modal-input (.. % -target -value))
                         :on-key-down #(utils/on-enter-down % (fn [text]
                                                                (when (goog.format.EmailAddress/isValidAddress text)
                                                                  (om/update-state! component assoc
                                                                                    :playground/modal-input ""
                                                                                    :playground/show-subscribe-modal? false)
                                                                  (om/transact! component `[(playground/subscribe ~{:email text})]))))}]
                       ((-> button/button button/hollow css/float-right)
                         {:on-click (fn []
                                      (let [text (:playground/modal-input (om/get-state component))]
                                        (when (goog.format.EmailAddress/isValidAddress text)
                                          (om/update-state! component assoc
                                                            :playground/modal-input ""
                                                            :playground/show-subscribe-modal? false)
                                          (om/transact! component `[(playground/subscribe ~{:email text})]))))}
                         "Subscribe")]]
                     [:div.content-section
                      [:small "Got feedback? We'd love to hear it! Shoot us an email at " [:a.mail-link "info@jourmoney.com"] " and let us know what you'd like to see in the product."]]]])})))

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
          (subscribe-modal this)]
         [:div#page-content
          {:ref (str ::page-content-ref)}
          (when factory
            (factory app-root))]
         [:div#footer-container
          (nav/->Footer)]]))))