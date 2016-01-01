(ns eponai.client.core
  (:require [eponai.client.app :as app]
            [eponai.client.backend :as backend]))

(defn ^:export run []
  (enable-console-print!)

  (println "Hello console")

  (app/run))
