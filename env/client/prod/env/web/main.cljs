(ns env.web.main
  (:require [eponai.web.app :as app]))

(defn ^:export runsulo []
      (app/run-prod))


;; A way for us to run cljsbuild-id release with fake auth (and other stuff).
(defn ^:export run_fake_sulo []
      (app/run-simple))
