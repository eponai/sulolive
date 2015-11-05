(ns flipmunks.budget.config
  (:require [clojure.edn :as edn]))

(def config
  (edn/read-string (slurp "budget-private/config.edn")))