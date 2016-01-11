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

(defn link-fb-user [conn user-id access-token email]
  ;; There's already a user account for the email provided by the FB account,
  ;; so just link the FB user to the existing account.
  (if-let [user (p/user (d/db conn) email)]
    (let [fb-user (f/fb-user-db-user user-id access-token (:db/id user))]
      (transact conn [fb-user]))

    ;; If we don't have a user account already, create a new one. If the FB account has an email,
    ;; the same will be used on the new user account.
    (let [user (f/user->db-user email)
          fb-user (f/fb-user-db-user user-id access-token (:db/id user))
          budget (f/db-budget (:db/id user))]
      ; If we are creating an account and received an email from the FB account, no need to verify the email.
      (if email
        (transact conn [user fb-user budget (f/->db-email-verification user :verification.status/verified)])
        (transact conn [user fb-user budget])))))

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