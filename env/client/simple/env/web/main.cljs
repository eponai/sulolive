(ns env.web.main
  (:require [eponai.web.app :as app]
            [eponai.client.devtools :as devtools]
            [cljs.loader]))

(defn ^:export runsulo []
      (devtools/install-app)
      (app/run-simple {}))

(cljs.loader/set-loaded! :main)