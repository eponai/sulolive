(ns eponai.web.ui.nav.sidebar
  (:require
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.client.routes :as routes]
    [eponai.common.shared :as shared]
    [eponai.client.auth :as auth]
    [eponai.web.social :as social]
    [om.next :as om :refer [defui]]
    [eponai.web.ui.nav.common :as nav.common]
    [eponai.common.mixpanel :as mixpanel]
    #?(:cljs
       [eponai.web.utils :as web.utils])
    [eponai.web.ui.button :as button]))

(defui Sidebar
  static om/IQuery
  (query [_]
    (nav.common/query))
  Object
  (render [this]
    (let [{:query/keys [auth owned-store navigation current-route]} (om/props this)
          {:keys [route]} current-route
          track-event (fn [k & [p]] (mixpanel/track-key k (merge p {:source "sidebar"})))]
      (dom/div
        (css/add-class :sidebar-container)
        (dom/div {:classes [:sidebar-overlay]})
        (dom/div
          {:id      "sulo-sidebar"
           :classes [:sidebar]}
          (if (and (some? route)
                   (or (= route :store-dashboard)
                       (= (namespace route) (name :store-dashboard))))
            ;; Store owner side menu
            [
             (menu/vertical
               nil
               (menu/item
                 (css/hide-for :large)
                 (menu/vertical
                   nil
                   (menu/item
                     (css/add-class :back)
                     (dom/a {:href    (routes/url :index)
                             :onClick #(mixpanel/track "Store: Go back to marketplace" {:source "sidebar"})}
                            (dom/strong nil (dom/small nil "Back to marketplace")))
                     )))
               (when (some? owned-store)
                 [
                  (menu/item
                    nil
                    (menu/vertical
                      nil
                      (menu/item
                        (when (= :store-dashboard (:route current-route))
                          (css/add-class :is-active))
                        (dom/a {:href    (routes/store-url owned-store :store-dashboard)
                                :onClick #(track-event ::mixpanel/go-to-dashboard)}
                               (dom/div {:classes ["icon icon-home"]})
                               (dom/span nil "Home")))
                      (menu/item
                        (when (= :store-dashboard/stream (:route current-route))
                          (css/add-class :is-active))
                        (dom/a {:href    (routes/store-url owned-store :store-dashboard/stream)
                                :onClick #(track-event ::mixpanel/go-to-stream-settings)}
                               (dom/div {:classes ["icon icon-stream"]})
                               (dom/span nil "Live stream")))

                      (menu/item
                        (when (contains? #{:store-dashboard/product-list
                                           :store-dashboard/create-product
                                           :store-dashboard/product} (:route current-route))
                          (css/add-class :is-active))
                        (dom/a {:href    (routes/store-url owned-store :store-dashboard/product-list)
                                :onClick #(track-event ::mixpanel/go-to-products)}
                               (dom/div {:classes ["icon icon-product"]})
                               (dom/span nil "Products")))
                      (menu/item
                        (when (contains? #{:store-dashboard/order-list
                                           :store-dashboard/order-list-new
                                           :store-dashboard/order-list-fulfilled
                                           :store-dashboard/order} (:route current-route))
                          (css/add-class :is-active))
                        (dom/a {:href    (routes/store-url owned-store :store-dashboard/order-list)
                                :onClick #(track-event ::mixpanel/go-to-orders)}
                               (dom/div {:classes ["icon icon-order"]})
                               (dom/span nil "Orders")))))
                  (menu/item
                    nil
                    (dom/label nil "Settings")
                    (menu/vertical
                      nil
                      (menu/item
                        (when (#{:store-dashboard/profile
                                 :store-dashboard/profile#options} (:route current-route))
                          (css/add-class :is-active))
                        (dom/a {:href    (routes/store-url owned-store :store-dashboard/profile)
                                :onClick #(track-event ::mixpanel/go-to-store-info)}
                               (dom/div {:classes ["icon icon-shop"]})
                               (dom/span nil "Store info")))

                      (menu/item
                        (when (contains? #{:store-dashboard/shipping} (:route current-route))
                          (css/add-class :is-active))
                        (dom/a {:href    (routes/store-url owned-store :store-dashboard/shipping)
                                :onClick #(track-event ::mixpanel/go-to-shipping)
                                }
                               (dom/div {:classes ["icon icon-truck"]})
                               (dom/span nil "Shipping")))
                      (menu/item
                        (when (#{:store-dashboard/business
                                 :store-dashboard/business#verify} (:route current-route))
                          (css/add-class :is-active))
                        (dom/a {:href    (routes/store-url owned-store :store-dashboard/business)
                                :onClick #(track-event ::mixpanel/go-to-business)}
                               (dom/div {:classes ["icon icon-business"]})
                               (dom/span nil "Business")))
                      (menu/item
                        (when (#{:store-dashboard/finances
                                 :store-dashboard/finances#settings
                                 :store-dashboard/finances#taxes} (:route current-route))
                          (css/add-class :is-active))
                        (dom/a {:href    (routes/store-url owned-store :store-dashboard/finances)
                                :onClick #(track-event ::mixpanel/go-to-finances)}
                               (dom/div {:classes ["icon icon-finances"]})
                               (dom/span nil "Finances")))))]))
             (menu/vertical
               (css/add-class :footer-menu)
               (menu/item
                 (css/hide-for :large)
                 (menu/vertical (css/add-class :signout-menu)
                                (if (some? auth)
                                  (menu/item nil (dom/a {:href    (routes/url :logout)
                                                         :onClick #(track-event ::mixpanel/signout)} (dom/small nil "Sign out")))
                                  (menu/item nil (dom/a (css/button {:onClick #(do
                                                                                (track-event ::mixpanel/open-signin)
                                                                                (auth/show-login (shared/by-key this :shared/login)))}) (dom/span nil "Sign up / Sign in"))))))
               (menu/item
                 nil
                 (menu/horizontal
                   {:key "social"}
                   (menu/item nil (social/sulo-social-link :social/facebook))
                   (menu/item nil (social/sulo-social-link :social/instagram))))
               (menu/item-text nil (social/sulo-icon-attribution) (social/sulo-copyright))
               )]


            ;; Consumer side menu.
            [(menu/vertical
               nil
               (menu/item
                 nil
                 (dom/label nil "Explore")
                 (menu/vertical
                   nil
                   (nav.common/collection-links this "sidebar")
                   ))
               (when (some? owned-store)
                 (menu/item
                   nil
                   (dom/label nil "Manage store")
                   (menu/vertical
                     nil
                     (let [store-name (get-in owned-store [:store/profile :store.profile/name])]
                       (menu/item
                         nil
                         (dom/a {:href    (routes/store-url owned-store :store-dashboard)
                                 :onClick #(do (track-event ::mixpanel/go-to-manage-store {:store-id   (:db/id owned-store)
                                                                                           :store-name store-name})
                                               )}
                                (dom/div {:classes ["icon icon-shop"]})
                                (dom/span nil store-name)))))))
               (when (some? auth)
                 (menu/item nil
                            (dom/label nil "Your account")
                            (menu/vertical
                              (css/add-class :your-account)
                              (menu/item
                                (when (= route (:route current-route))
                                  (css/add-class :is-active))
                                (dom/a {:href    (routes/url :user/order-list {:user-id (:db/id auth)})
                                        :onClick #(track-event ::mixpanel/go-to-purchases)}
                                       (dom/span nil "Purchases")))
                              (menu/item
                                (when (= route (:route current-route))
                                  (css/add-class :is-active))
                                (dom/a {:href    (routes/url :user-settings {:user-id (:db/id auth)})
                                        :onClick #(track-event ::mixpanel/go-to-settings)}
                                       (dom/span nil "Settings")))
                              (menu/item
                                nil
                                (dom/a {:href    (routes/url :landing-page)
                                        :onClick #(track-event ::mixpanel/change-location)}
                                       (dom/span nil "Change location"))))))
               )
             (menu/vertical
               (css/add-class :footer-menu)
               (menu/item
                 (css/hide-for :large)
                 (menu/vertical (css/add-class :signout-menu)
                                (when (and (some? auth)
                                           (nil? owned-store))
                                  (menu/item nil (dom/a
                                                   {:href    (routes/url :sell)
                                                    :onClick #(track-event ::mixpanel/go-to-start-store)} (dom/span nil "Start a store"))))
                                (if (some? auth)
                                  (menu/item nil (dom/a {:href    (routes/url :logout)
                                                         :onClick #(track-event ::mixpanel/signout)}
                                                        (dom/small nil "Sign out")))
                                  (menu/item
                                    (css/add-class :sign-in-item)
                                    (button/store-navigation-cta
                                      {:href (routes/url :sell)}
                                      (dom/span nil "Open your LIVE shop")

                                      )
                                    )


                                  )))
               (menu/item-text nil (dom/small {:classes ["copyright"]} "© eponai hb 2017")))]
            ))))))

(def ->Sidebar (om/factory Sidebar))