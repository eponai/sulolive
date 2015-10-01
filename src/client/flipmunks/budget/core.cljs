(ns flipmunks.budget.core
  (:require [flipmunks.budget.omtest :as om]
            [flipmunks.budget.backend :as backend]))

(enable-console-print!)

(println "Hello console")

(om/run (backend/get-dates))

