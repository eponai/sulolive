(ns eponai.mobile.ios.ui.root
  (:require [eponai.client.ui :as ui :refer-macros [opts]]
            [eponai.mobile.components :refer [navigation-experimental-header
                                              navigation-experimental-header-title
                                              navigation-experimental-card-stack
                                              navigator-ios
                                              modal]]
            [eponai.mobile.om-helper :as om-helper :refer-macros [with-om-vars]]
            [eponai.mobile.ios.routes.ui-handlers :as ui.routes]
            [eponai.mobile.ios.ui.tab-bar-main :refer [->LoggedIn LoggedIn]]
            [eponai.mobile.ios.linking :as linking]
            [goog.object :as gobj]
            [medley.core :as medley]
            [om.next :as om :refer-macros [defui]]
            [taoensso.timbre :refer-macros [debug]]
            [eponai.mobile.ios.ui.signup :refer [->LoginMenu ->ActivateAccount ActivateAccount]]))

;(comment
;  (def route->transition
;   {:route/login        {:route/transactions js/ReactNative.Navigator.SceneConfigs.FloatFromBottom}
;    :route/transactions {:route/login js/ReactNative.Navigator.SceneConfigs.FloatFromBottom}}))

(defn props->route [props]
  (get-in props [:query/app :ui.component.app/route]))

(defn header [props]
  (navigation-experimental-header
    {:render-title-component #(navigation-experimental-header-title "Title")}))

(defui RootView
  static om/IQuery
  (query [_]
    [:datascript/schema
     :user/current
     {:query/app [:ui.component.app/route]}
     ;{:routing/app-root (medley/map-vals #(->> % :component om/get-query)
     ;                                    ui.routes/route-key->root-handler)}
     {:query/auth [{:ui.singleton.auth/user [{:user/status [:db/ident]}
                                             :user/email
                                             :user/uuid]}]}
     {:proxy/logged-in (om/get-query LoggedIn)}
     ])
  Object
  (navigate [this]
    (let [nav (om/react-ref this "navigator")
          {:keys [query/auth]} (om/props this)
          current-user (:ui.singleton.auth/user auth)]
      (when nav
        (if-let [user-status (get-in current-user [:user/status :db/ident])]
          (when (= user-status :user.status/new)
            (.replace nav #js {:title     ""
                               :component ->ActivateAccount
                               :passProps #js {:user       current-user
                                               :onActivate (fn [params]
                                                             (om/transact! this `[(session.signin/activate ~params)
                                                                                  :user/current
                                                                                  :query/auth
                                                                                  :proxy/logged-in]))
                                               :onLogout   (fn []
                                                             (om/transact! this `[(session/signout)
                                                                                  :user/current
                                                                                  :query/auth]))}}))
          (.replace nav #js {:title     ""
                             :component ->LoginMenu
                             :passProps #js {:onFacebookLogin (fn [params]
                                                                (om/transact! this `[(session.signin/facebook ~params)
                                                                                     :user/current
                                                                                     :query/auth
                                                                                     :proxy/logged-in]))
                                             :onEmailLogin    (fn [params]
                                                                (om/transact! this `[(session.signin/email ~(assoc params :device :ios))]))}})))))
  (initLocalState [this]
    ;; Need to pass the identical function to add/removeEventListener.
    {:url-handler (fn [event] (linking/load-url! this (gobj/get event "url")))})
  (componentDidMount [this]
    (.addEventListener linking/linking "url" (-> this om/get-state :url-handler))
    (.navigate this))
  (componentWillUnmount [this]
    (.removeEventListener linking/linking "url", (-> this om/get-state :url-handler)))
  (componentDidUpdate [this _ _]
    (.navigate this))
  (render [this]
    (let [{:keys [routing/app-root
                  query/auth
                  proxy/logged-in] :as props} (om/props this)
          current-user (:ui.singleton.auth/user auth)
          ;route (props->route props)
          ;factory (get-in ui.routes/route-handler->ui-component [route :factory])
          ;nav (om/react-ref this "navigator")
          ;{:keys [query/auth]} (om/props this)
          ;current-user (:ui.singleton.auth/user auth)
          user-status (get-in current-user [:user/status :db/ident])
          ]
      ;(when-not factory
      ;  (debug "No factory found for route: " route " props: " props))

      (debug "User-status: " user-status)
      (if (= user-status :user.status/active)
        (->LoggedIn (om/computed logged-in
                                 {:onLogout (fn []
                                              (om/transact! this `[(session/signout)
                                                                   :user/current
                                                                   :query/auth]))
                                  :user current-user}))
        (modal (opts {:animationType "slide"
                      :visible true})
               (navigator-ios {:initialRoute {:title     ""
                                              :component ->LoginMenu
                                              :passProps {:onLogin (fn [res]
                                                                     (om/transact! this `[(signin/facebook ~res)
                                                                                          :user/current
                                                                                          :query/auth]))}}
                               :ref          "navigator"
                               :style        {:flex 1}
                               :translucent  false
                               :barTintColor "#01213d"
                               :shadowHidden true}))))))
