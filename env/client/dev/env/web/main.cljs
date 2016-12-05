(ns env.web.main
  (:require [eponai.web.app :as app]
            [eponai.client.utils :as utils]
            [eponai.web.ui.stream :as stream]))

(set! js/window.mixpanel #js {"track" (fn [& args] )})

(defn ^:export run []
  (utils/install-app)
  (app/run))

(defn ^:export runstream []
  (utils/install-app)
  (stream/run))
