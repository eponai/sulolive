(ns flipmunks.budget.datomic_dev
  (:require [flipmunks.budget.core :as core]
            [flipmunks.budget.datomic.core :as budget.d]
            [datomic.api :as d]))

(def app
  (do 
    ;; set the core/conn var
    (alter-var-root #'budget.d/conn
                    (fn [old-val] (d/connect "datomic:dev://localhost:4334/test-budget")))
    ;; reutrn the core/app ring handler
    core/app))

