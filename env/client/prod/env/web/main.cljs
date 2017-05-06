(ns env.web.main
  (:require [eponai.web.app :as app]))

(defn ^:export runsulo []
      (app/run-prod))
