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
    [eponai.client.utils :as utils]
    [eponai.common.ui.dom :as dom]
    [eponai.web.ui.nav.navbar :as navbar]
    [eponai.web.ui.nav.sidebar :as sidebar]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.coming-soon :as coming-soon]
    #?(:cljs [eponai.web.firebase :as firebase])
    [eponai.web.ui.nav.footer :as foot]
    [clojure.string :as string]
    [eponai.client.routes :as routes]))

(def dom-app-id "the-sulo-app")
(def root-route-key :routing/app-root)


(defn normalize-route
  "We need to normalize our routes now that we have namespaced route matches.
  The namespaced route matches help us set new routes."
  [route]
  (if-let [ns (namespace route)]
    (keyword ns)
    route))

(defmulti route->component normalize-route)
(defmethod route->component :default [_] nil)

(defn transact-route!
  "Warning: Probably not what you want to use. See: (set-route!) instead.

  Set's the app route given either a reconciler or a component, and a route.
   Also optinally takes
   :route-params - a map with route specific parameters.
   :queue? - re-renders from the root component, defaults to true
   :tx - an additional transaction to be transacted after the route has been set.
         Can be a mutation, a read-key or a vector of those things.

   Examples:
   - Set route to be a store:
     (set-route! this :store {:route-params {:store-id 12345}})
   - Set route to index and re-read :cart/price
     (set-route! this :index {:tx :cart/price})
     (set-route! this :index {:tx [:cart/price]})

   Heavily inspired by compassus/set-route! (github.com/compassus/compassus),
   which we doesn't use because it's too frameworky (We'd have to use its parser
   for example, and I'm not sure it works with datascript(?))."
  ([x route]
   (transact-route! x route nil))
  ([x route {:keys [delayed-queue queue? route-params query-params tx] :or {queue? true}}]
   {:pre [(or (om/reconciler? x) (om/component? x))
          (keyword? route)]}
   (let [reconciler (cond-> x (om/component? x) (om/get-reconciler))
         tx (when-not (nil? tx)
              (cond->> tx (not (vector? tx)) (vector)))
         reads-fn (fn []
                    (om/get-query (om/app-root reconciler))
                    )
         tx (cond-> [(list 'routes/set-route! {:route        route
                                               :route-params route-params
                                               :query-params query-params})]
                    :always (into tx)
                    queue? (into (reads-fn)))]
     (debug "Transacting tx: " tx)
     (om/transact! reconciler tx)
     (when (fn? delayed-queue)
       (delayed-queue #(om/transact! reconciler (reads-fn)))))))

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
             :user :unauthorized :user-settings
             :landing-page :about :not-found :tos :stores])

(defui Router
  static om/IQuery
  (query [this]
    [:query/current-route
     :query/locations
     {:proxy/navbar (om/get-query navbar/Navbar)}
     {:proxy/sidebar (om/get-query sidebar/Sidebar)}
     {:proxy/footer (om/get-query foot/Footer)}
     {:proxy/coming-soon (om/get-query coming-soon/ComingSoon)}
     {:routing/app-root (into {}
                              (map (juxt identity #(or (some-> (route->component %) :component om/get-query)
                                                       [])))
                              routes)}])
  Object
  #?(:cljs
     (shouldComponentUpdate
       [this props state]
       (utils/should-update-when-route-is-loaded this props state)))
  #?(:cljs
     (componentDidUpdate [this prev-props _]
                         ;; TODO: Change this to shared/by-key when merged with other branch.
                         ;; (scroll-helper/scroll-on-did-render (shared/by-key this :shared/scroll-helper))
                         (when (not= (:query/current-route (om/props this))
                                     (:query/current-route prev-props))
                           (firebase/route-changed (shared/by-key this :shared/firebase)
                                                   (routes/with-normalized-route-params this (:query/current-route (om/props this)))
                                                   (routes/with-normalized-route-params this (:query/current-route prev-props))))))
  (render [this]
    (let [{:keys       [routing/app-root query/current-route query/locations]
           :proxy/keys [navbar sidebar footer coming-soon]} (om/props this)
          route (normalize-route (:route current-route))
          {:keys [factory component]} (route->component route)]
      (when (nil? component)
        (error "Sorry. No component found for route: " route
               ". You have to call (router/register-component <route> <component>) at the end of your component"
               ". You also have to require your component's namespace in eponai.common.ui_namespaces.cljc"
               ". We're making it this complicated because we want module code splitting. "))

      (dom/div
        (css/add-class "sulo-page"
                       {:id (str "sulo-" (or (not-empty (namespace route)) (name route)))})
        (dom/div
          (css/add-class :page-container)
          (navbar/->Navbar navbar)
          (dom/div
            (css/add-class :page-content-container {:key "content-container"})
            (sidebar/->Sidebar sidebar)
            (dom/div
              (css/add-class :page-content)

              (let [current-loc-path (:locality (:route-params current-route))]
                (if (contains? #{"yul"} current-loc-path)
                  (coming-soon/->ComingSoon coming-soon)
                  (factory app-root)))))
          ;(when-not no-footer?)
          (foot/->Footer footer)
          )))))

