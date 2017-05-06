(ns env.web.main
  (:require [eponai.web.app :as app]
            [eponai.client.devtools :as devtools]
    ;;[plomber.core :as plomber]
            [eponai.common.ui.help :as help]
            [eponai.common.ui.checkout :as checkout]
            [eponai.common.ui.shopping-bag :as bag]
            [eponai.common.ui.store :as store]
            [eponai.common.ui.store.dashboard :as store-dashboard]
            [eponai.common.ui.goods :as goods]
            [eponai.common.ui.index :as index]
            [eponai.common.ui.product-page :as product-page]
            [eponai.common.ui.streams :as streams]
            [eponai.common.ui.user :as user]
            [eponai.web.ui.coming-soon :as coming-soon]
            [eponai.web.ui.start-store :as start-store]
            [eponai.web.ui.login :as login]
            [eponai.web.ui.unauthorized :as unauthorized]
            ))

(set! js/window.mixpanel #js {"track" (fn [& args] )})

(defn ^:export runsulo []
  (devtools/install-app)
  (app/run-dev {
                ;;::plomber   (plomber/instrument)
                }))