(ns eponai.mobile.ios.ui.root
  (:require [eponai.client.ui :as ui :refer-macros [opts]]
            [eponai.mobile.components :refer [navigation-experimental-header
                                              navigation-experimental-header-title
                                              navigation-experimental-card-stack]]
            [eponai.mobile.om-helper :as om-helper :refer-macros [with-om-vars]]
            [eponai.mobile.ios.routes.ui-handlers :as ui.routes]
            [eponai.mobile.ios.linking :as linking]
            [goog.object :as gobj]
            [medley.core :as medley]
            [om.next :as om :refer-macros [defui]]
            [taoensso.timbre :refer-macros [debug]]))

(comment
  (def route->transition
   {:route/login        {:route/transactions js/ReactNative.Navigator.SceneConfigs.FloatFromBottom}
    :route/transactions {:route/login js/ReactNative.Navigator.SceneConfigs.FloatFromBottom}}))

(defn props->route [props]
  (get-in props [:query/app :ui.component.app/route]))

(defn header [props]
  (navigation-experimental-header
    {:render-title-component #(navigation-experimental-header-title "Title")}))

(defui ^:once RootView
  static om/IQuery
  (query [_]
    [:datascript/schema
     :user/current
     {:query/app [:ui.component.app/route]}
     {:routing/app-root (medley/map-vals #(->> % :component om/get-query)
                                         ui.routes/route-key->root-handler)}])
  Object
  (initLocalState [this]
    ;; Need to pass the identical function to add/removeEventListener.
    {:url-handler (fn [event] (linking/load-url! this (gobj/get event "url")))})
  (componentDidMount [this]
    (.addEventListener linking/linking "url" (-> this om/get-state :url-handler)))
  (componentWillUnmount [this]
    (.removeEventListener linking/linking "url", (-> this om/get-state :url-handler)))

  (render [this]
    (let [{:keys [routing/app-root] :as props} (om/props this)
          route (props->route props)
          factory (get-in ui.routes/route-handler->ui-component [route :factory])]
      (when-not factory
        (debug "No factory found for route: " route " props: " props))
      (factory app-root))))

(comment
  (render-scene [this scene-props]
                (with-om-vars
                  this
                  ))
  (navigation-experimental-card-stack {
                                       :onNavigate      #(om/transact! this `[(root/navigate-pop {:value %})])
                                       :renderOverlay   #(header %)
                                       :navigationState (-> (om/props this)
                                                            :query/root
                                                            :ui.component.root/navigation-state
                                                            clj->js)
                                       ;; :on-did-focus     #(.forceUpdate this)
                                       :renderScene     #(.render-scene this %)}))
