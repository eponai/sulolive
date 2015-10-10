(ns flipmunks.budget.datomic_mem
  (:require [flipmunks.budget.core :as core]
            [flipmunks.budget.datomic.core :as budget.d]
            [datomic.api :as d]))

(def app
  (do 
    ;; set the core/conn var
    (alter-var-root #'budget.d/conn
                    (fn [old-val] 
                      (let [uri "datomic:mem://test-db"]
                        (if (d/create-database uri)
                          (d/connect uri)
                          (throw (Exception. "Could not create datomic db with uri: " uri))))))
    ;; reutrn the core/app ring handler
    core/app))

