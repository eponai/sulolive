(ns flipmunks.budget.datomic_dev
  (:require [flipmunks.budget.core :as core]
            [flipmunks.budget.datomic.core :as budget.d]
            [flipmunks.budget.datomic_mem :as mem]
            [datomic.api :as d]))

(defn connect []
  (alter-var-root #'core/conn
                  (fn [old-val] 
                    (let [uri "datomic:dev://localhost:4334/test-budget"] 
                      (try 
                        (d/connect uri)
                        (catch Exception e
                          (prn (str "Exception:" e " when trying to connect to datomic=" uri))
                          (prn "Will try to set up inmemory db...")
                          (let [mem-conn (mem/create-new-inmemory-db)]
                            (mem/add-data-to-connection mem-conn)
                            (prn "Successfully set up inmemory db!")
                            mem-conn)))))))

(def app
  (do 
    ;; set the core/conn var
    (connect)
    ;; reutrn the core/app ring handler
    core/app))

