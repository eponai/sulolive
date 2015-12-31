(ns eponai.repl
  (:require [eponai.server.core :as core]
            [clojure.tools.namespace.repl :as ns.repl]
            [clojure.repl :refer :all]))

(defn init []
  ;; Do not reload our namespace so we can keep our repl defs.
  (ns.repl/disable-reload!)
  (prn "****************************************************")
  (prn "Done initializing our eponai repl!")
  (prn "Use (refresh) to reload all namespaces while keeping")
  (prn "the vars defined in your repl.")
  (prn "****************************************************"))


(defn refresh []
  "refresh all namespaces and re-require our namespaces"
  (ns.repl/refresh))

