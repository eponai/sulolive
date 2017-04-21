(ns eponai.server.core
  (:gen-class)
  (:require
    [com.stuartsierra.component :as component]
    [environ.core :as env]
    [eponai.common.validate]
    [eponai.server.parser.read]
    [eponai.server.parser.mutate]
    [eponai.server.middleware :as m]
    [aleph.netty]
    [taoensso.timbre :refer [debug error info]]
    ;; Dev/debug require
    [eponai.server.system :as system]
    [clojure.spec :as s]))

(defn- make-system [opts]
  (let [default-port 3000
        env-port (try
                   (Long/parseLong (env/env :port))
                   (catch Exception e
                     nil))
        port (or (:port opts) env-port default-port)
        _ (info "Using port: " port)
        config (merge {:port port :env env/env} opts)]
    (if (::dev-system config)
      (system/dev-system config)
      (system/prod-system config))))

(defn -main [& _]
  (let [system (component/start-system (make-system {}))
        server (get-in system [:system/aleph :server])]
    (aleph.netty/wait-for-close server)))

(defn main-release-no-ssl
  []
  (debug "Running repl in production mode without ssl")
  (component/start-system
    (make-system {::system/disable-ssl true})))

(defn make-dev-system
  "For repl, debug and test usages. Takes an optional map. Returns the aleph-server."
  [& [opts]]
  {:pre [(or (nil? opts) (map? opts))]}
  (s/check-asserts true)
  (make-system (merge {::dev-system true} opts)))

(defn system-for-tests [& [{:keys [conn port] :as opts}]]
  {:pre [(or (nil? opts) (map? opts))]}
  (make-dev-system
    (merge {:port                  (or port 0)
            ::system/provided-conn conn}
           opts)))
