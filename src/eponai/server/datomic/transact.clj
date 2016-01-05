(ns eponai.server.datomic.transact
  (:require [datomic.api :as d]
            [eponai.common.transact :refer [transact]]
            [eponai.server.datomic.format :as f]
            [eponai.server.datomic.validate :as v]
            [eponai.server.datomic.pull :as p]))

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

(defn new-fb-user
  [conn user-id access-token email]
  (let [fb-user (f/fb-user-db-user user-id access-token email)]
    (println "Transacted fb user: " fb-user)
    (transact conn [fb-user])))

(defn currency-rates
  "Transact conversions into datomic.

  Throws ExceptionInfo if transaction failed."
  [conn rates]
  (let [db-rates (f/cur-rates->db-txs rates)]
    (transact conn db-rates)))

(defn currencies
  "Transact currencies into datomic.

  Throws ExceptionInto if transaction failed."
  [conn currencies]
  (transact conn (f/curs->db-txs currencies)))

(defn currency-infos
  [conn cur-infos]
  (let [db-cur-codes (->> (p/currencies (d/db conn))
                         (map #(keyword (:currency/code %)))
                         set)
        cur-infos (->> cur-infos
                       (filter #(contains? db-cur-codes (key %))))]
    (transact conn (f/cur-infos->db-txs (vals cur-infos)))))