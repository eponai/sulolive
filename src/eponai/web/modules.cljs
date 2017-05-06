(ns eponai.web.modules
  (:require
    [goog.module :as module]
    [goog.module.ModuleManager :as module-manager]
    [goog.module.ModuleLoader]
    [medley.core :as medley]
    [taoensso.timbre :refer [debug error]])
  (:import goog.module.ModuleManager))

(defprotocol IModuleLoader
  (loaded-route? [this route])
  (require-route! [this route callback]))

(defn dev-modules []
  ;; Modules are always loaded in dev, because modules only work in advanced and simple compilation.
  (reify IModuleLoader
    (loaded-route? [this route]
      true)
    (require-route! [this route callback]
      (callback route))))

(defn- route->module [route]
  (or (namespace route)
      (name route)))

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
             (error "Exception calling execOnLoad: " e " route: " route))))))

(def manager (module-manager/getInstance))
(def loader (goog.module.ModuleLoader.))

(defn advanced-compilation-modules [routes]
  (let [route->js-file (into {} (comp (map route->module)
                                      (map (juxt identity #(vector (str "/js/out/closure-modules/" % ".js")))))
                             routes)
        _ (debug "route->js-file: " route->js-file)
        module-infos (medley/map-vals (constantly []) route->js-file)
        _ (debug "module-infos: " module-infos)
        modules (clj->js route->js-file)
        module-info (clj->js module-infos)
        ]
    ;;(when js/goog.DEBUG)
    ;;(.setDebugMode loader true)
    (.setLoader manager loader)
    (.setAllModuleInfo manager module-info)
    (.setModuleUris manager modules)
    (ModuleLoader. manager)))

(defn set-loaded!
  "Mark a module as loaded"
  [route]
  (debug "Will mark module" (route->module route) "loaded! route: " route)
  (try (->
         (.getInstance goog.module.ModuleManager)
         (.setLoaded (route->module route)))
       (debug "DID mark module" (route->module route) "loaded!")
       (catch :default e
         (error "Unable to mark module: " (route->module route) "as loaded: " e))))