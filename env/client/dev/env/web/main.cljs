(ns env.web.main
  (:require [eponai.web.app :as app]
            [eponai.client.devtools :as devtools]))

(set! js/window.mixpanel #js {"track" (fn [& args] )})

(defn ^:export runsulo []
  (devtools/install-app)
  (app/run-dev))