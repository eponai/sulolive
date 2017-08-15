(ns eponai.web.ui.nav.common
  (:require
    [om.next :as om]
    [eponai.client.routes :as routes]
    [eponai.client.auth :as client.auth]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.dom :as dom]
    #?(:cljs
       [eponai.web.utils :as web-utils])
    [eponai.common.mixpanel :as mixpanel]
    [eponai.web.ui.login :as login]
    [eponai.web.ui.nav.loading-bar :as loading]
    [eponai.web.ui.notifications :as note]
    [taoensso.timbre :refer [debug]]))

(defn collection-links [component source]
  (let [{:query/keys [auth locations navigation]} (om/props component)]
    (map
      (fn [{:category/keys [route-map name path] :as a}]
        (let [{:keys [route route-params]} route-map
              opts {:href    (routes/url route route-params)
                    :onClick #(do (mixpanel/track-key ::mixpanel/shop-by-category {:source   source
                                                                                   :category path})
                                  ;(when (empty? locations)
                                  ;  #?(:cljs
                                  ;     (when-let [locs (web-utils/element-by-id "sulo-locations")]
                                  ;       (web-utils/scroll-to locs 250))))
                                  )}]
          (menu/item-link
            (cond->> (css/add-class :category opts)
                     (= source "navbar")
                     (css/show-for :large))
            (dom/span nil (str name)))))
      navigation)))

(defn live-link [component source]
  (let [{:query/keys [auth locations]} (om/props component)]
    (menu/item-link
      (->> {:href    (routes/url :live)
            :onClick #(do
                       (mixpanel/track-key ::mixpanel/shop-live {:source source})
                       ;(when (empty? locations)
                       ;  #?(:cljs
                       ;     (when-let [locs (web-utils/element-by-id "sulo-locations")]
                       ;       (web-utils/scroll-to locs 250))))
                       )}
           (css/add-classes [:category :navbar-live])
           (css/show-for :large))
      (dom/span
        nil
        ;; Wrap in span for server and client to render the same html
        (dom/span nil "Live")))))

(defn query []
  [{:query/cart [{:user.cart/items [:db/id
                                    {:store.item/_skus [{:store/_items [{:store/status [:status/type]}]}]}]}
                 ;; To link the cart with the user.
                 {:user/_cart [:db/id]}]}
   {:query/auth [:db/id
                 :user/email
                 {:user/stripe [:stripe/id]}
                 {:user/profile [:user.profile/name
                                 {:user.profile/photo [:photo/path :photo/id]}]}]}
   {:query/owned-store [:db/id
                        :store/username
                        {:store/locality [:sulo-locality/path]}
                        {:store/profile [:store.profile/name {:store.profile/photo [:photo/path]}]}
                        ;; to be able to query the store on the client side.
                        {:store/owners [{:store.owner/user [:db/id]}]}]}
   {:query/navigation [:category/name :category/label :category/path :category/route-map]}
   {:proxy/loading-bar (om/get-query loading/LoadingBar)}
   {:proxy/login-modal (om/get-query login/LoginModal)}
   {:proxy/notification (om/get-query note/Notifications)}
   :query/current-route])