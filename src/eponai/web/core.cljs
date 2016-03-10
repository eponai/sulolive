(ns eponai.web.core
  (:require [eponai.web.app :as app]
            [eponai.web.signup :as signup]
            [eponai.client.utils :as utils]
            ))

(defn ^:export run []
  (utils/install-app)
  (app/run))

(defn ^:export runsignup []
  (utils/install-app)
  (signup/run))
