(ns eponai.mobile.ios.core
  (:require [eponai.mobile.react-helper]
            [cljsjs.firebase]
            [eponai.mobile.ios.app :as app]))

(defn init [config]
  (app/run config))