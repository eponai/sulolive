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
  (prn " (refresh) to reload all namespaces")
  (prn " (start-server) to start the server")
  (prn " (set-level level) to set logger level")
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

(defn refresh []
  "refresh all namespaces and re-require our namespaces"
  (when @server-atom
    (stop @server-atom))
  (ns.repl/refresh)
  (when @server-atom
    (start-server)))


