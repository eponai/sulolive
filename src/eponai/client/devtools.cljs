(ns eponai.client.devtools
  (:require [devtools.core :as devtools]
            [eponai.client.logger :as logger]))

(defn install-app []
  (enable-console-print!)
  (devtools/install!)
  (logger/install-logger!))