(ns env.web.main
  (:require [eponai.web.app :as app]
            [taoensso.timbre :as timbre]))

(taoensso.timbre/set-level! :info)

(defn ^:export runsulo []
      (enable-console-print!)
      (app/run-prod))