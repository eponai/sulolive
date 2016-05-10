(ns env.web.main
  (:require [eponai.web.app :as app]
            [eponai.web.signup :as signup]
            [eponai.web.playground :as playground]
            [taoensso.timbre :as timbre]))

(taoensso.timbre/set-level! :info)

(defn ^:export run []
  (enable-console-print!)
  (app/run))

(defn ^:export runsignup []
  (enable-console-print!)
  (signup/run))

(defn ^:export runplayground []
  (enable-console-print!)
  (playground/run))
