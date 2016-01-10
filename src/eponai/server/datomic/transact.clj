(ns eponai.server.datomic.transact
  (:require [datomic.api :as d]
            [eponai.common.database.transact :refer [transact]]
            [eponai.server.datomic.format :as f]
            [eponai.server.datomic.validate :as v]
            [eponai.server.datomic.pull :as p]))

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

(defn email-verification [conn entity status]
  (let [ver (f/->db-email-verification entity status)]
    (transact conn [ver])
    ver))

(defn new-user
  "Transact a new user into datomic. Creates a budget associated with the account,
  and a verification to be used for the email.

  Throws ExceptionInfo if transaction failed."
  [conn email]
  (when (v/valid-signup? email)
    (let [db-user (f/user->db-user email)
          db-verification (f/->db-email-verification db-user :verification.status/pending)
          db-budget (f/db-budget (db-user :db/id))]
      (transact conn [db-user
                      db-verification
                      db-budget])
      db-verification)))

(defn new-fb-user
  "Transact a new Facebook user into datomic, using a facebook user-id and an access-token recieved from Facebook.

  If db-user is provided, the Facebook account will be linked to that user."
  ([conn user-id access-token]
    (new-fb-user conn user-id access-token nil))
  ([conn user-id access-token db-user]
   (let [fb-user (f/fb-user-db-user user-id access-token db-user)]
     (transact conn [fb-user]))))

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