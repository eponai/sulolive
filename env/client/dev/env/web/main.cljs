(ns env.web.main
  (:require [eponai.web.app :as app]
            [eponai.web.signup :as signup]
            [eponai.web.playground :as playground]
            [eponai.client.utils :as utils]))

(set! js/window.mixpanel #js {"track" (fn [& args] )})

(defn ^:export run []
  (utils/install-app)
  (app/run))

(defn ^:export runsignup []
  (utils/install-app)
  (signup/run))

(defn ^:export runplayground []
  (utils/install-app)
  (playground/run))
