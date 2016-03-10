(ns eponai.client.utils
  (:require [devtools.core :as devtools]
            [eponai.client.logger :as logger]
            [taoensso.timbre :as timbre :refer-macros [debug]]))


(defn set-level [l]
  (timbre/set-level! l))

(def set-trace #(set-level :trace))
(def set-debug #(set-level :debug))

(defn install-app []
  (enable-console-print!)
  (devtools/enable-feature! :sanity-hints :dirac)
  (devtools/install!)
  (logger/install-logger!))
