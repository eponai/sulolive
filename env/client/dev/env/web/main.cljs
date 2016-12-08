(ns env.web.main
  (:require [eponai.web.app :as app]
            [eponai.client.utils :as utils]
            [eponai.client.run :as run]))

(set! js/window.mixpanel #js {"track" (fn [& args] )})

(defn ^:export runstore[]
      (utils/install-app)
      (run/run :store))

(defn ^:export rungoods []
      (utils/install-app)
      (run/run :goods))

(defn ^:export runcheckout []
      (utils/install-app)
      (run/run :checkout))
