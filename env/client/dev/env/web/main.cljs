(ns env.web.main
  (:require [eponai.web.app :as app]
            [eponai.client.devtools :as devtools]
            [plomber.core :as plomber]
            [eponai.common.ui-namespaces]
            [cljs.loader]))

;(set! js/window.mixpanel #js {"track" (fn [& args] )})

(defn ^:export runsulo []
  (devtools/install-app)
  (app/run-dev {
                ;;::plomber   (plomber/instrument)
                }))

(cljs.loader/set-loaded! :main)