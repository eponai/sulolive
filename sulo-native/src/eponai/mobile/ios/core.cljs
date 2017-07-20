(ns eponai.mobile.ios.core
  (:require [eponai.mobile.react-helper]
            [cljsjs.firebase]
            [eponai.client.devtools :as utils]
            [eponai.mobile.ios.app :as app]))

(defn init [config]
  (utils/install-app)
  (app/run config))