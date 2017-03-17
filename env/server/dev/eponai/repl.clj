(ns eponai.repl
  (:require [eponai.server.core :as core]
            [reloaded.repl :refer [system start stop go reset reset-all]]
            [taoensso.timbre :as timbre]))

(defn init []
  (prn "****************************************************")
  (prn "                REPL TIPS AND TRICKS:")
  (prn " ** System Commands: ")
  (prn " [init-system system start stop go reset reset-all]")
  (prn " ** Other: ")
  (prn " (set-debug) - set logger level to debug")
  (prn " (set-trace) - set logger level to trace")
  (prn " (set-level level) - set logger level to level")
  (prn " eponai.server.core is aliased :as core")
  (prn "****************************************************")
  (reloaded.repl/set-init! #(core/main-debug)))

(def init-system reloaded.repl/init)
;; start-server was the old command we've gotten used to.
(def start-server init-system)

(defn set-level [level]
  (timbre/set-level! level))

(defn set-trace []
  (set-level :trace))

(defn set-debug []
  (set-level :debug))