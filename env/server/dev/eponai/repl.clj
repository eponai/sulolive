(ns eponai.repl
  (:require [eponai.server.core :as core]
            [eponai.server.datomic-dev :as datomic_dev]
            [eponai.common.database :as db]
            [datomic.api :as datomic]
            [clojure.tools.namespace.repl :as ns.repl]
            [taoensso.timbre :as timbre]
            [clojure.repl :refer :all]))

(defn init []
  ;; Do not reload our namespace so we can keep our repl defs.
  (ns.repl/disable-reload!)
  (prn "****************************************************")
  (prn "                REPL TIPS AND TRICKS:")
  (prn " (refresh) - reload all namespaces")
  (prn " (start-server) - start the server")
  (prn " (stop-server) - stop the server")
  (prn " (reset-db) - reset db so next server gets fresh db")
  (prn " (set-debug) - set logger level to debug")
  (prn " (set-trace) - set logger level to trace")
  (prn " (set-level level) - set logger level to level")
  (prn " eponai.server.core is aliased :as core")
  (prn "****************************************************"))

(defn stop [server]
  (.stop server))

(def server-atom (atom nil))

(defn start-server []
  (prn "****************************************************")
  (prn " Started server!")
  (prn " Run (stop-server) to stop the server")
  (reset! server-atom (core/main-debug))
  (prn "****************************************************")
  @server-atom)

(defn stop-server []
  (stop @server-atom))

(defn set-level [level]
  (timbre/set-level! level))

(defn set-trace []
  (set-level :trace))

(defn set-debug []
  (set-level :debug))

(defn refresh []
  "refresh all namespaces and re-require our namespaces"
  (when @server-atom
    (stop @server-atom))
  (ns.repl/refresh)
  (when @server-atom
    (start-server)))

(defn reset-db []
  (reset! datomic_dev/connection nil))

