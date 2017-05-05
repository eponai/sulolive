(ns eponai.common.ui.router
  (:require
    [om.next :as om :refer [defui]]
    [om.dom]
    [taoensso.timbre :refer [error debug info]]
    #?(:cljs [eponai.web.modules :as modules])))

(def dom-app-id "the-sulo-app")

(defn normalize-route
  "We need to normalize our routes now that we have namespaced route matches.
  The namespaced route matches help us set new routes."
  [route]
  (if-let [ns (namespace route)]
    (keyword ns)
    route))

(defmulti route->component normalize-route)

;; Routes set by eponai.common.ui.components
(def routes (atom nil))

(defui Router
  static om/IQuery
  (query [this]
    (if-let [routes @routes]
      [:query/current-route
       {:routing/app-root (into {}
                                (map (fn [route]
                                       (let [{:keys [query component]} (route->component route)]
                                         [route (if component
                                                  (om/get-query component)
                                                  query)])))
                                routes)}]
      (error "Routes was nil. Make sure you've required eponai.common.ui.components in from your main entrypoint, e.g. app.cljs")))
  Object
  (ensure-route-is-loaded [this props]
    #?(:cljs
       (let [next-route (normalize-route (get-in props [:query/current-route :route]))
             {:shared/keys [modules]} (om/shared this)
             set-next-route-loaded! #(do
                                       (info "Setting route as loaded: " next-route)
                                       (om/update-state! this assoc-in [:loaded next-route] true))]
         (info "Where is our code going?")
         (if (modules/loaded-route? modules next-route)
           (do
             (info "Route was loaded: " next-route)
             (when-not (get-in (om/get-state this) [:loaded next-route])
               (set-next-route-loaded!)))
           (do
             (info "Route was not loaded!: " next-route)
             (modules/require-route! modules next-route set-next-route-loaded!)
             (om/update-state! this assoc :prev-route (:route (:query/current-route (om/props this)))))))))
  (initLocalState [this]
    {:loaded #?(:cljs {}
                :clj  (into {} (map (juxt identity (constantly true))) @routes))})
  (componentWillReceiveProps [this props]
    (.ensure-route-is-loaded this props))
  (componentWillMount [this]
    (info "WE'RE IN WILL MOUNT!!")
    (.ensure-route-is-loaded this (om/props this)))
  (render [this]
    (let [{:keys [routing/app-root query/current-route]} (om/props this)
          route (normalize-route (:route current-route))
          render-route (if (get-in (om/get-state this) [:loaded route])
                         route
                         (:prev-route (om/get-state this)))
          _ (when (nil? render-route)
              (error "Current nor previous route was loaded. Nothing to render I'm afraid. current-route: " current-route " render-route: " render-route " state: " (om/get-state this)))
          {:keys [factory component]} (route->component render-route)
          _ (debug "route->component methods: " (methods route->component))
          _ (when (nil? component)
              (error "Sorry. No component found for route: " render-route
                     ". Make sure to implement multimethod router/route->component in your component's namespace"
                     " for route: " render-route
                     ". You also have to place your component and route in: eponai.common.ui.components.clj"
                     ". You also have to require your components namespace in /env/client/dev/env/web/main/cljs"
                     ". We're making it this complicated because we want module code splitting."))
          factory (or factory (om/factory component))]
      (factory app-root))))
