(ns eponai.mobile.ios.core
  (:require [eponai.mobile.react-helper]                       ;; require this first!
            [devtools.core :as devtools]
            [eponai.client.logger :as logger]
            [eponai.mobile.ios.app :as app]))

;; Sort of copy of common.core
;; TODO: DRY it.
(defn install-app! []
  (enable-console-print!)
  (devtools/enable-feature! :sanity-hints :dirac)
  (devtools/install!)
  (logger/install-logger!))

(defn init []
  (install-app!)
  (app/run))