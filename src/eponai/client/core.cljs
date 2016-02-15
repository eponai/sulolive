(ns eponai.client.core
  (:require [devtools.core :as devtools]
            [eponai.client.app :as app]
            [eponai.client.logger :as logger]
            [eponai.client.signup :as signup]
            [taoensso.timbre :as timbre :refer-macros [debug]]))

(defn set-level [l]
  (timbre/set-level! l))

(def set-trace #(set-level :trace))
(def set-debug #(set-level :debug))

(defn install-app []
  (enable-console-print!)
  (devtools/enable-feature! :sanity-hints :dirac)
  (devtools/install!)
  (logger/install-logger!))

(defn ^:export run []
  (install-app)
  (app/run))

(defn ^:export runsignup []
  (install-app)
  (signup/run))
