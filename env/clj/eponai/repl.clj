(ns eponai.repl
  (:require [eponai.server.core :as core]
            [clojure.tools.namespace.repl :as ns.repl]
            [taoensso.timbre :as timbre]
            [clojure.repl :refer :all]))

(defn init []
  ;; Do not reload our namespace so we can keep our repl defs.
  (ns.repl/disable-reload!)
  (prn "****************************************************")
  (prn "                REPL TIPS AND TRICKS:")
  (prn "* (refresh) to reload all namespaces")
  (prn "* (start-server) to start the server")
  (prn "* (set-level level) to set logger level")
  (prn "  where level can be :debug :error :trace etc..")
  (prn "* eponai.server.core is aliased :as core")
  (prn "****************************************************"))

(defn refresh []
  "refresh all namespaces and re-require our namespaces"
  (ns.repl/refresh))

(defn start-server []
  (let [s (def server (core/main-debug))]
    (prn "****************************************************")
    (prn "Ran (def server (core/main-debug))")
    (prn "You can run (stop server) to stop the server")
    (prn "****************************************************")
    s))

(defn stop [server]
  (.stop server))

(defn set-level [level]
  (timbre/set-level! level))

