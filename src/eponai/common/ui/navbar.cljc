(ns eponai.common.ui.navbar
  (:require
    [eponai.common.ui.common :as common]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug error]]))

(defui Navbar
  static om/IQuery
  (query [_]
    [{:query/cart [:cart/price
                   {:cart/items [:item/price
                                 :item/img-src
                                 :item/name]}]}])
  Object
  #?(:cljs
     (signin [this]
             (let [{:keys [lock]} (om/get-state this)
                   options (clj->js {:connections ["facebook" "google-oauth2"]
                                     :callbackURL "http://localhost:3000"
                                     :authParams {:scope "openid email"}
                                     :connectionScopes {"facebook" ["email" "public_profile" "user_friends"]}
                                     :primaryColor "#9A4B4F"
                                     :languageDictionary {
                                                          :title "My Company"
                                                          }
                                     :avatar {:displayName (fn [email cb])}})]
               ;callbackURL: 'http://localhost:3000/',
               ;responseType: 'token',
               ;authParams: {
               ;             state: getUrlPath(),
               ;                    scope: 'openid profile'
               ;(.socialOrMagiclink lock options)
               (.show lock options)
               ;(.show lock)
               )))

  ;lock.magiclink({
  ;                callbackURL: 'https://YOUR_APP/callback',
  ;                             authParams: {
  ;                                          scope: 'openid email' // Learn about scopes: https://auth0.com/docs/scopes
  ;                                          }
  ;                });

  (initLocalState [_]
    {:cart-open? false})
  (componentDidMount [this]
    #?(:cljs
       (when js/Auth0LockPasswordless
         (let [auth-options (clj->js {
                                      ;:connectionScopes {"facebook" ["email" "public_profile" "user_friends"]}
                                      ;:title "SULO"
                                      ;:redirect false
                                      :auth {:redirect false
                                             :params {
                                                      :scope "openid email" ;// Learn about scopes: https://auth0.com/docs/scopes
                                                      }}})
               lock (new js/Auth0Lock "JMqCBngHgOcSYBwlVCG2htrKxQFldzDh" "sulo.auth0.com" auth-options)]
           (.. lock
               (on "authenticated" (fn [authResult]
                                     (.. lock
                                         (getProfile (.-idToken authResult) (fn [error profile]
                                                                              (if (some? error)
                                                                                (error "Got error: " error)
                                                                                (do
                                                                                  (debug "Got token: " (.-idToken authResult))
                                                                                  (.setItem js/localStorage "idToken" (.-idToken authResult))
                                                                                  (.setItem js/localStorage "profile" (js/JSON.stringify profile))))))))))
           (om/update-state! this assoc :lock lock)))))

  ;lock.on("authenticated", function(authResult) {
  ;                                               // Use the token in authResult to getProfile() and save it to localStorage
  ;                                               lock.getProfile(authResult.idToken, function(error, profile) {
  ;                                                                                                             if (error) {
  ;                                                                                                                         // Handle error
  ;                                                                                                                            return;
  ;                                                                                                                         }
  ;
  ;                                                                                                                localStorage.setItem('idToken', authResult.idToken);
  ;                                                                                                                localStorage.setItem('profile', JSON.stringify(profile));
  ;                                                                                                             });
  ;                                               });
  (render [this]
    (let [{:keys [cart-open?]} (om/get-state this)
          {:keys [query/cart]} (om/props this)]
      (dom/div #js {:id "sulo-navbar"}
        (dom/nav #js {:className "navbar top-bar"}
                 (dom/div #js {:className "top-bar-left"}
                   (dom/ul #js {:className "menu"}
                           (dom/li nil
                                   (dom/a #js {:className "navbar-brand"
                                               :href      "/"}
                                          "Sulo"))
                           ;#?(:cljs
                           ;   (dom/li nil
                           ;           (dom/a #js {:onClick #(.signin this)} "Sign in")))
                           ))

                 (dom/div #js {:className "top-bar-right shopping-cart"}
                   (dom/ul #js {:className "menu"}
                           (dom/li nil
                                   (dom/a #js {:href "/checkout"
                                               ;:onClick #(om/update-state! this update :cart-open? not)
                                               }
                                          (dom/span nil (common/two-decimal-price (:cart/price cart)))
                                          (dom/i #js {:className "fa fa-shopping-cart fa-fw"}))))

                   ;(when cart-open?)
                   (dom/div #js {:className "cart-container dropdown-pane"}
                     (dom/div nil
                       (apply dom/ul #js {:className "cart menu vertical"}
                              (map (fn [i]
                                     (dom/li nil
                                             (dom/div #js {:className "row collapse align-middle content-item"}
                                               (dom/div #js {:className "columns small-2"}
                                                 (dom/div #js {:className "photo-container"}
                                                   (dom/div #js {:className "photo square thumbnail" :style #js {:backgroundImage (str "url(" (:item/img-src i) ")")}})))
                                               (dom/div #js {:className "columns small-10"}
                                                 (dom/div #js {:className "content-item-title-section"}
                                                   (dom/small #js {:className "name"} (:item/name i)))
                                                 (dom/div #js {:className "content-item-subtitle-section"}
                                                   (dom/span #js {:className "price"}
                                                             (common/two-decimal-price (:item/price i))))))))
                                   (take 3 (:cart/items cart))))

                       (dom/div #js {:className "callout nude"}
                         (when (< 3 (count (:cart/items cart)))
                           (dom/small nil (str "You have " (- (count (:cart/items cart)) 3) " more item(s) in your bag")))
                         (dom/h5 nil "Total: " (dom/strong nil (common/two-decimal-price (:cart/price cart)))))
                       (dom/a #js {:className "button expanded"
                                   :href      "/checkout"} "View My Bag")))))))))

(def ->Navbar (om/factory Navbar))