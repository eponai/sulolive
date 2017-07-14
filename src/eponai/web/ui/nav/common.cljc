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
    [eponai.web.ui.notifications :as note]))

(defn navbar-route [component href]
  (let [{:query/keys [auth locations]} (om/props component)]
    (when (some? auth)
      (if (nil? href)
        (routes/url :landing-page/locality)
        href))))

(defn collection-links [component source]
  (let [{:query/keys [auth locations navigation]} (om/props component)]
    (map
      (fn [{:category/keys [route-map name path] :as a}]
        (let [opts {:href    (navbar-route
                               component
                               (when-let [loc (:sulo-locality/path (client.auth/current-locality component))]
                                 (routes/map->url (assoc-in route-map [:route-params :locality] loc))))
                    :classes (when (nil? auth) [:unauthed])
                    :onClick #(do (mixpanel/track-key ::mixpanel/shop-by-category {:source   source
                                                                                   :category path})
                                  (when (empty? locations)
                                    #?(:cljs
                                       (when-let [locs (web-utils/element-by-id "sulo-locations")]
                                         (web-utils/scroll-to locs 250)))))}]
          (menu/item-link
            (cond->> (css/add-class :category opts)
                     (= source "navbar")
                     (css/show-for :large))
            (dom/span nil (str name)))))
      navigation)))

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
   :query/locations
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