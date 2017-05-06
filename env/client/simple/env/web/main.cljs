(ns env.web.main
  (:require [eponai.web.app :as app]
            [eponai.client.devtools :as devtools]
            ))

(defn ^:export runsulo []
      (devtools/install-app)
      (app/run-simple {}))
