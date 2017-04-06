(ns env.web.main
  (:require [eponai.web.app :as app]
            [eponai.client.utils :as utils]))

(set! js/window.mixpanel #js {"track" (fn [& args] )})

(defn ^:export runsulo []
      (utils/install-app)
      (app/run-dev))