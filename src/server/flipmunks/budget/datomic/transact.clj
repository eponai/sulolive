(ns flipmunks.budget.datomic.transact
  (:require [datomic.api :as d]))


(defn transact
  "Transact a collecion of entites into datomic."
  [conn txs]
  @(d/transact conn txs))
