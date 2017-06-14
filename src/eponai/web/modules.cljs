(ns eponai.web.modules
  (:require
    [goog.module]
    [goog.module.ModuleManager :as module-manager]
    [goog.module.ModuleLoader]
    [taoensso.timbre :refer [debug error warn]])
  (:import goog.module.ModuleManager))

(def non-route-modules [:stream+chat :photo-uploader])
(def dependencies {:login           [:index]
                   :user-settings   [:photo-uploader]
                   :user            [:photo-uploader]
                   :store           [:stream+chat]
                   :store-dashboard [:photo-uploader :stream+chat]})

(defprotocol IRouteModuleLoader
  (loaded-route? [this route] "Returns true if route has been loaded.")
  (require-route! [this route callback]
                  "Requires a route. Calls callback when it has been loaded. Immediatley if route was already loaded.")
  (prefetch-route [this route]
                  "Downloads the module but doesn't evaluate the javascript, thus not setting it to loaded."))

(def loader (goog.module.ModuleLoader.))
(def manager (doto (module-manager/getInstance)
               (.setLoader loader)))

(defn- get-asset-version
  "Returns the version of our assets. One way to avoid browser cache issues between releases."
  []
  (or (some-> (.getElementById js/document "asset-version-meta")
              (.-content))
      "unset_asset_version"))

(defn- route->module
  "Turns a route into a module name."
  [route]
  (or (namespace route)
      (name route)))

(defn init-manager [routes]
  (let [asset-version (get-asset-version)
        routes (into (set routes) non-route-modules)
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
  (let [manager (.getInstance goog.module.ModuleManager)
        module (route->module route)
        module-info (.getModuleInfo manager module)]
    (if (= js/undefined module-info)
      (swap! routes-loaded-pre-init conj route)
      (try
        (.setLoaded manager module)
        (catch :default e
          (error "Unable to mark module: " (route->module route) "as loaded: " e))))))

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
                          (error "Error after calling callback: " e))))
        (catch :default e
          (error "Exception calling execOnLoad: " e " route: " route)))))
  (prefetch-route [this route]
    (when-not (loaded-route? this route)
      (try
        (.prefetchModule manager (route->module route))
        (catch :default e
          (error "Got error prefetching route: " route " error:" e))))))

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
