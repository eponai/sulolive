(ns eponai.server.core
  (:gen-class)
  (:require
    [com.stuartsierra.component :as component]
    [environ.core :as env]
    [eponai.common.validate]
    [eponai.server.parser.read]
    [eponai.server.parser.mutate]
    [eponai.server.routes :refer [site-routes]]
    [eponai.server.middleware :as m]
    [taoensso.timbre :refer [debug error info]]
    ;; Dev/debug require
    [eponai.server.system :as system]
    [clojure.spec :as s]
    ))

(defn- start-system
  ([] (start-system {}))
  ([opts]
   (let [default-port 3000
         env-port (try
                    (Long/parseLong (env/env :port))
                    (catch Exception e
                      nil))
         port (or (:port opts) env-port default-port)
         _ (info "Using port: " port)
         config (merge {:port port :env env/env}
                       opts)
         system (if (::dev-system config)
                  (system/dev-system config)
                  (system/prod-system config))]
     (component/start-system system))))

(defn -main [& _]
  (start-system))

(defn main-debug
  "For repl, debug and test usages. Takes an optional map. Returns the aleph-server."
  [& [opts]]
  {:pre [(or (nil? opts) (map? opts))]}
  (s/check-asserts true)
  (start-system (merge {::dev-system true} opts)))

(defn main-release-no-ssl
  []
  (debug "Running repl in production mode without ssl")
  (start-system {::system/disable-ssl true}))

(defn start-server-for-tests [& [{:keys [conn port] :as opts}]]
  {:pre [(or (nil? opts) (map? opts))]}
  (start-system
    (merge {::dev-system       true
            :port              (or port 0)
            ::system/provided-conn    conn}
           opts)))
