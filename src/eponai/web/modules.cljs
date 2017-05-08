(ns eponai.web.modules
  (:require
    [goog.module :as module]
    [goog.module.ModuleManager :as module-manager]
    [goog.module.ModuleLoader]
    [medley.core :as medley]
    [taoensso.timbre :refer [debug error warn]])
  (:import goog.module.ModuleManager))


(defprotocol IModuleLoader
  (loaded-route? [this route])
  (require-route! [this route callback])
  (prefetch-route [this route]))

(def extra-groupings [:react-select :stream+chat :photo-uploader])
(def dependencies {:login           [:index]
                   :user            [:photo-uploader]
                   :store           [:stream+chat]
                   :store-dashboard [:photo-uploader :react-select :stream+chat]})

(def loader (goog.module.ModuleLoader.))
(def manager (doto (module-manager/getInstance)
               (.setLoader loader)))

(defn- route->module [route]
  (or (namespace route)
      (name route)))

(defn init-manager [routes]
  (let [routes (into (set routes) extra-groupings)
        route->js-file (into {} (comp (map route->module)
                                      (map (juxt identity #(vector (str "/js/out/closure-modules/" % ".js")))))
                             routes)
        module-infos (into {}
                           (map (juxt route->module #(into [] (map route->module) (get dependencies % []))))
                           routes)
        modules (clj->js route->js-file)
        module-info (clj->js module-infos)]

    (debug "route->js-file: " route->js-file)
    (debug "module-infos: " module-infos)

    (doto manager
      (.setAllModuleInfo module-info)
      (.setModuleUris modules))))

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
  IModuleLoader
  (loaded-route? [this route]
    (let [module-id (route->module route)
          module (.getModuleInfo manager module-id)
          ret (some-> module (.isLoaded))]
      (debug "Loaded-route: " route " id: " module-id " module:" module " loaded?: " ret)
      ret))
  (require-route! [this route callback]
    (debug "Requiring route: " route)
    (if (loaded-route? this route)
      (do (debug "ALREADY Required route: " route)
          (callback route))
      (try (.execOnLoad manager (route->module route)
                        #(do
                           (debug "Required route!!!!!: " route)
                           (try
                             (callback route)
                             (catch :default e
                               (debug "Error after calling callback: " e)))))
           (debug "CALLED .execOnLoad with module: " (route->module route))
           (catch :default e
             (error "Exception calling execOnLoad: " e " route: " route)))))
  (prefetch-route [this route]
    (when-not (loaded-route? this route)
      (try
        (.prefetchModule manager (route->module route))
        (catch :default e
          (error "Got error prefetching route: " route " error:" e))))))

(defn advanced-compilation-modules [routes]
  (when-let [routes (seq @routes-loaded-pre-init)]
    (throw (ex-info "Some routes were loaded before initializing modules: " routes
                    {:routes routes
                     :cause  :loaded-pre-init})))
  (ModuleLoader. (init-manager routes)))

(defn dev-modules [routes]
  (init-manager routes)
  (when-let [routes (seq @routes-loaded-pre-init)]
    (run! set-loaded! routes))
  ;; Modules are always loaded in dev, because modules only work in advanced and simple compilation.
  (reify IModuleLoader
    (loaded-route? [this route]
      true)
    (require-route! [this route callback]
      (callback route))
    (prefetch-route [this route]
      nil)))
