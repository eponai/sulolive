(ns env.web.main
  (:require [eponai.web.app :as app]
            [eponai.web.ui.stream :as stream]
            [taoensso.timbre :as timbre]))

(taoensso.timbre/set-level! :info)

(defn ^:export run []
  (enable-console-print!)
  (app/run))


(defn ^:export runstream []
  (enable-console-print!)
  (stream/run))
