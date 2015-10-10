(ns flipmunks.budget.datomic_dev
  (:require [flipmunks.budget.core :as core]
            [flipmunks.budget.datomic.core :as budget.d]
            [datomic.api :as d]))

(defn connect []
  (alter-var-root #'budget.d/conn
                  (fn [old-val] (d/connect "datomic:dev://localhost:4334/test-budget"))))

(def app
  (do 
    ;; set the core/conn var
    (connect)
    ;; reutrn the core/app ring handler
    core/app))

