(ns env.web.main
  (:require [eponai.web.app :as app]
            [eponai.web.signup :as signup]))

(defn ^:export run []
  (enable-console-print!)
  (app/run))

(defn ^:export runsignup []
  (enable-console-print!)
  (signup/run))
