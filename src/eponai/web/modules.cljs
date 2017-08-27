(ns eponai.web.modules
  (:require
    [goog.module]
    [goog.module.ModuleManager :as module-manager]
    [goog.module.ModuleLoader]
    [taoensso.timbre :refer [debug error warn]]
    [eponai.web.utils :as web.utils]
    [cljs.loader])
  (:import goog.module.ModuleManager))

(def dependencies {:login           [:index]})

(defprotocol IRouteModuleLoader
  (loaded-route? [this route] "Returns true if route has been loaded.")
  (require-route! [this route callback]
                  "Requires a route. Calls callback when it has been loaded. Immediatley if route was already loaded.")
  (prefetch-route [this route]
                  "Downloads the module but doesn't evaluate the javascript, thus not setting it to loaded."))

(declare loader manager)
;(def loader (goog.module.ModuleLoader.))
;(def manager (doto (module-manager/getInstance)
;               (.setLoader loader)))

(defn- get-asset-version
  "Returns the version of our assets. One way to avoid browser cache issues between releases."
  []
  (or (web.utils/meta-content-by-id "asset-version-meta")
      "unset_asset_version"))

(defn- route->module
  "Turns a route into a module name."
  [route]
  (or (namespace route)
      (name route)))

(defn init-manager [routes]
  (let [asset-version (get-asset-version)
        routes (into (set routes))
        route->js-file (into {} (comp (map route->module)
                                      (map (juxt identity #(vector (str "/js/out/closure-modules/" % ".js?v=" asset-version)))))
                             routes)
        module-infos (into {}
                           (map (juxt route->module #(into [] (map route->module) (get dependencies % []))))
                           routes)]
    (doto manager
      (.setAllModuleInfo (clj->js module-infos))
      (.setModuleUris (clj->js route->js-file)))))

;; Adding all routes loaded before we've initialized the module manager
;; here. This is useful for both dev and release (advanced) builds.
;; Release: Finds modules which are included by require instead of by
;;          included by loading a module.
;; Dev: All modules should end up here. So here we can find modules which
;;      aren't included by require but should be.
(def routes-loaded-pre-init (atom #{}))

(defn set-loaded!
  "Mark a module as loaded"
  [route]
  (cljs.loader/set-loaded! route))

(defrecord ModuleLoader [manager]
  IRouteModuleLoader
  (loaded-route? [this route]
    (let [module-id (route->module route)
          module (.getModuleInfo manager module-id)]
      (some-> module (.isLoaded))))
  (require-route! [this route callback]
    (if (loaded-route? this route)
      (callback route)
      (try
        (.execOnLoad manager (route->module route)
                     #(try
                        (callback route)
                        (catch :default e
                          (error "Error after calling callback: " e " route: " route))))
        (catch :default e
          (error "Exception calling execOnLoad: " e " route: " route)))))
  (prefetch-route [this route]
    (when-not (or (loaded-route? this route)
                  (.isModuleLoading manager (route->module route)))
      (try
        (.prefetchModule manager (route->module route))
        (catch :default e
          (error "Got error prefetching route: " route " error:" e))))))

(defn cljs-loader-modules []
  (debug "uris: " MODULE_URIS)
  (let [route->cljs-module (fn [route]
                             (keyword (or (namespace route) (name route))))]
    (reify IRouteModuleLoader
     (loaded-route? [this route]
       (cljs.loader/loaded? (route->cljs-module route)))
     (require-route! [this route callback]
       (cljs.loader/load (route->cljs-module route) callback))
     (prefetch-route [this route]
       (cljs.loader/prefetch (route->cljs-module route))))))

(defn advanced-compilation-modules
  "Module loader for advanced compilation.
  Assumes none of the modules have been loaded by :require. Throws exception when a module has."
  [routes]
  (when-let [routes-pre-init (seq @routes-loaded-pre-init)]
    (throw (ex-info "Some routes were loaded before initializing modules: " routes-pre-init
                    {:routes routes-pre-init
                     :cause  :loaded-pre-init})))
  (ModuleLoader. (init-manager routes)))

(defn dev-modules
  "Module loader for development. In dev every module should be loaded by :require
  because optimizations :none doesn't support closure modules."
  [routes]
  (init-manager routes)
  (when-let [routes-pre-init (seq @routes-loaded-pre-init)]
    ;; Now that we've initialized the manager, we can set routes loaded.
    ;; Not because it should make a difference, but it makes the dev fake
    ;; implementation closer to the real implementaiton.
    (run! set-loaded! routes-pre-init))
  (reify IRouteModuleLoader
    (loaded-route? [this route]
      true)
    (require-route! [this route callback]
      (callback route))
    (prefetch-route [this route]
      nil)))
