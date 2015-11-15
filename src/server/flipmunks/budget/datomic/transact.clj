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
  #{:transaction/uuid :transaction/name :transcation/date :transction/amount
  :transaction/currency :transaction/created-at}.

  Throws ExceptionInfo if the given input is invalid, or if transaction failed."
  [conn user-txs]
  (when (v/valid-user-txs? user-txs)
    (let [txs (f/user-owned-txs->dbtxs user-txs)]
      (transact conn txs))))

(defn new-verification [conn entity attribute status]
  (let [ver (f/db-entity->db-verification entity attribute status)]
    (transact conn [ver])))

(defn add
  "Make a datomic transaction usind :db/add. Can be used to update attribute values.

  Throws ExceptionInfo with {:cause ::transaction-error} if transaction fails."
  [conn entid attr val]
  (transact conn [[:db/add entid attr val]]))

(defn retract
  "Make a datomic transaction using :db/retract.

  Throws ExceptionInfo with {:cause ::transaction-error} if transaction fails."
  [conn entid attr val]
  (transact conn [[:db/retract entid attr val]]))

(defn new-user
  "Transact a new user into datomic.

  Throws ExceptionInfo if transaction failed."
  [conn new-user]
  (when (v/valid-signup? new-user)
    (let [[db-user db-password] (f/user->db-user-password new-user)
          db-verification (f/db-entity->db-verification db-user :user/email :verification.status/pending)
          db-budget (f/db-budget (db-user :db/id))]
      (transact conn [db-user
                      db-password
                      db-verification
                      db-budget]))))

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