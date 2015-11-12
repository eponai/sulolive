(ns flipmunks.budget.datomic.transact
  (:require [datomic.api :as d]
            [flipmunks.budget.datomic.format :as f]
            [flipmunks.budget.datomic.validate :as v]
            [flipmunks.budget.http :as e]))


(defn- transact
  "Transact a collecion of entites into datomic.
  Throws ExceptionInfo if transaction failed."
  [conn txs]
  (try
    @(d/transact conn txs)
    (catch Exception e
      (throw (ex-info (.getMessage e) {:cause ::transaction-error
                                       :status ::e/service-unavailable
                                       :data {:conn conn
                                              :txs txs}
                                       :message (.getMessage e)
                                       :exception e})))))

(defn user-txs
  "Put the user transaction maps into datomic. Will fail if one or
  more of the following required fields are not included in the map:
  #{:uuid :name :date :amount :currency}.

  Throws ExceptionInfo if the given input is invalid, or if transaction failed."
  [conn user-email user-txs]
  (when (v/valid-user-txs? user-txs)
    (let [txs (f/user-owned-txs->dbtxs user-email user-txs)]
      (transact conn txs))))

(defn new-verification [conn entity attribute status]
  (let [ver (f/db-entity->db-verification entity attribute status)]
    (transact conn [ver])))

(defn new-user
  "Transact a new user into datomic.

  Throws ExceptionInfo if transaction failed."
  [conn new-user]
  (when (v/valid-signup? new-user)
    (transact conn [(f/user->db-user new-user)])))

(defn currency-rates
  "Transact conversions into datomic.

  Throws ExceptionInfo if transaction failed."
  [conn rate-ms]
  (let [db-rates (apply concat (map #(f/cur-rates->db-txs %) rate-ms))]
    (transact conn db-rates)))

(defn currencies
  "Transact currencies into datomic.

  Throws ExceptionInto if transaction failed."
  [conn currencies]
  (transact conn (f/curs->db-txs currencies)))