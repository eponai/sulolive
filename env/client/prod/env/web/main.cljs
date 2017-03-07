(ns env.web.main
  (:require [eponai.web.app :as app]
            [eponai.client.run :as run]
            [taoensso.timbre :as timbre]))

(taoensso.timbre/set-level! :info)

(defn ^:export runsulo []
      (enable-console-print!)
      (run/run-prod))