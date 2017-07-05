(ns env.web.main
  (:require-macros [eponai.common.macros :refer [when-not-timbre-level]])
  (:require [eponai.web.app :as app]
    ;;[eponai.client.devtools :as devtools]
            ))

(defn ^:export runsulo []
      (app/run-prod))


;; Only leaving the run_fake_sulo method in when there's a timbre-level set.
(when-not-timbre-level
  ;; A way for us to run cljsbuild-id release with fake auth (and other stuff).
  (defn ^:export run_fake_sulo []
        ;; For debugging:
        ;; (devtools/install-app)
        (app/run-simple)))
