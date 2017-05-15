(ns eponai.common.ui.router
  #?(:cljs (:require-macros [eponai.common.ui.router :as router-macros]))
  (:require
    [om.next :as om :refer [defui]]
    [om.dom]
    [eponai.common.shared :as shared]
    [taoensso.timbre :refer [error debug info]]
    #?(:cljs [eponai.web.modules :as modules])
    #?(:cljs [eponai.web.scroll-helper :as scroll-helper])
    #?(:cljs [goog.object :as gobj])
    [eponai.client.utils :as utils]))

(def dom-app-id "the-sulo-app")

(defn normalize-route
  "We need to normalize our routes now that we have namespaced route matches.
  The namespaced route matches help us set new routes."
  [route]
  (if-let [ns (namespace route)]
    (keyword ns)
    route))

(defmulti route->component normalize-route)
(defmethod route->component :default [_] nil)

(defn register-component
  "Registers a component to a route for the router.

  Call on the last line of your component's namespace with the route
  the component is associated with."
  [route component]
  (defmethod route->component route
    [_]
    {:component component
     :factory   (om/factory component)})
  #?(:cljs
     (modules/set-loaded! route)))

(def routes [:index :store :browse :checkout :store-dashboard
             :shopping-bag :login :sell :product :live :help
             :user :coming-soon :unauthorized])

#?(:cljs
   (defn should-update-when-route-is-loaded
     "Returns true when route is loaded and the default om shouldComponentUpdate returns true."
     [this props state]
     (let [next-route (some-> (utils/shouldComponentUpdate-next-props props)
                              (get-in [:query/current-route :route]))]

       (and (or (nil? next-route)
                (modules/loaded-route? (shared/by-key this :shared/modules) next-route))
            (utils/shouldComponentUpdate-om this props state)))))

(defui Router
  static om/IQuery
  (query [this]
    [:query/current-route
     {:routing/app-root (into {}
                              (map (juxt identity #(or (some-> (route->component %) :component om/get-query)
                                                       [])))
                              routes)}])
  Object
  #?(:cljs
     (shouldComponentUpdate
       [this props state]
       (should-update-when-route-is-loaded this props state)))
  (componentDidUpdate [this _ _]

       ;; TODO: Change this to shared/by-key when merged with other branch.
       ;; (scroll-helper/scroll-on-did-render (shared/by-key this :shared/scroll-helper))
       )
  (render [this]
    (let [{:keys [routing/app-root query/current-route]} (om/props this)
          route (normalize-route (:route current-route))
          {:keys [factory component]} (route->component route)]
      (when (nil? component)
        (error "Sorry. No component found for route: " route
               ". You have to call (router/register-component <route> <component>) at the end of your component"
               ". You also have to require your component's namespace in eponai.common.ui_namespaces.cljc"
               ". We're making it this complicated because we want module code splitting."))
      (factory app-root))))

