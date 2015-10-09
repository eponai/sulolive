(ns flipmunks.budget.core
  (:require [flipmunks.budget.omtest :as om]
            [flipmunks.budget.om_next_test :as om-next]
            [flipmunks.budget.backend :as backend]))

(enable-console-print!)

(println "Hello console")

(comment (om/run (backend/get-dates)))
(om-next/run)

