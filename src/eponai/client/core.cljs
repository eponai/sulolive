(ns eponai.client.core
  (:require [eponai.client.app :as app]
            [eponai.client.signup :as signup]
            [eponai.client.verify :as verify]))

(defn ^:export run []
  (enable-console-print!)

  (println "Hello console")

  (app/run))

(defn ^:export runverify []
  (verify/run))

(defn ^:export runsignup []
  (signup/run))