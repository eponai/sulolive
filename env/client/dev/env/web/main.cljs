(ns env.web.main
  (:require [eponai.web.app :as app]
            [eponai.client.devtools :as devtools]
            [plomber.core :as plomber]
            [eponai.common.ui-namespaces]))

(defn ^:export runsulo []
  (devtools/install-app)
  (app/run-dev {
                ;;::plomber   (plomber/instrument)
                }))

(defn ^:export rundemo []
      (devtools/install-app)
      (app/run-demo))
