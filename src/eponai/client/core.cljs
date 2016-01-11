(ns eponai.client.core
  (:require [eponai.client.app :as app]
            [eponai.client.signup :as signup]))

(defn ^:export run []
  (enable-console-print!)

  (println "Hello console")

  (app/run))

(defn ^:export runsignup []
  (signup/run))