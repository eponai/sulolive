(ns eponai.common.ui.router
  (:require
    [om.next :as om :refer [defui]]
    [om.dom]
    [taoensso.timbre :refer [error]]))

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
  (render [this]
    (let [{:keys [routing/app-root query/current-route]} (om/props this)
          route (:route current-route :index)
          {:keys [factory component]} (route->component route)
          _ (when (nil? component)
              (error "Sorry. No component found for route: " route
                     ". Make sure to implement multimethod router/route->component in your component's namespace"
                     " for route: " route
                     ". You also have to place your component and route in: eponai.common.ui.components.clj"
                     ". We're making it this complicated because we want module code splitting."))
          factory (or factory (om/factory component))]
      (factory app-root))))
