(ns env.web.main
  (:require [eponai.web.app :as app]
            [eponai.client.run :as run]
            [taoensso.timbre :as timbre]))

(taoensso.timbre/set-level! :info)

(defn ^:export run []
  (enable-console-print!)
  (app/run))


(defn ^:export runstream []
  (enable-console-print!)
  (run/store))
