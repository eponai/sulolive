(ns flipmunks.budget.core
  (:require [flipmunks.budget.app :as app]
            [flipmunks.budget.backend :as backend]))

(enable-console-print!)

(println "Hello console")

(app/run
  (backend/data-provider))

