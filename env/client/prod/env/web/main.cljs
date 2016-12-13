(ns env.web.main
  (:require [eponai.web.app :as app]
            [eponai.client.run :as run]
            [taoensso.timbre :as timbre]))

(taoensso.timbre/set-level! :info)

(defn ^:export runstore[]
  (enable-console-print!)
  (run/run :store))

(defn ^:export rungoods []
  (enable-console-print!)
  (run/run :goods))

(defn ^:export runcheckout []
  (enable-console-print!)
  (run/run :checkout))

(defn ^:export runindex []
  (enable-console-print!)
  (run/run :index))
