(ns env.web.main
  (:require [eponai.web.app :as app]
            [taoensso.timbre :as timbre])
  (:import goog.debug.Console)
  )

;;(taoensso.timbre/set-level! :info)

(defn ^:export runsulo []
      (enable-console-print!)
      (.setCapturing (goog.debug.Console.) true)
      (app/run-prod))
