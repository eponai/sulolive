(ns eponai.repl
  ;; require and use more namespaces to have them
  ;; available when the repl starts.
  (:require [eponai.server.core :as core :refer [main-debug]]
            [clojure.tools.namespace.repl :refer [refresh]]))


