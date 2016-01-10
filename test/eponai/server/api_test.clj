(ns eponai.server.api-test
  (:require [clojure.test :refer :all]
            [datomic.api :as d]
            [eponai.server.api :as api]
            [eponai.server.datomic.format :as f]
            [clj-time.core :as t]
            [eponai.common.database.pull :as p]
            [clj-time.coerce :as c])
  (:import (clojure.lang ExceptionInfo)))

(def schema (read-string (slurp "resources/private/datomic-schema.edn")))

(def user "user@email.com")

(defn- new-db
  "Creates an empty database and returns the connection."
  [txs]
  (let [uri "datomic:mem://test-db"]
    (d/delete-database uri)
    (d/create-database uri)
    (let [conn (d/connect uri)]
      (d/transact conn schema)
      (when txs
        (d/transact conn txs))
      conn)))

;;;; ------ api/verify tests ---------

; Success case
(deftest verification-pending-and-not-expired
  (testing "Verification is pending, and was created less than 15 minutes ago. Should set status to :verification.status/verified"
    (let [db-user (f/user->db-user user)
          db-verification (f/->db-email-verification db-user :verification.status/pending)
          uuid (str (:verification/uuid db-verification))
          conn (new-db [db-user
                        db-verification])
          _ (api/verify-email conn uuid)]
      (is (= (:db/id (:verification/status (p/verification (d/db conn) '[*] uuid)))
             (d/entid (d/db conn) :verification.status/verified))))))

; Failure cases
(deftest verify-uuid-does-not-exist
  (testing "Verification UUID does not exist. Should throw exception for invalid UUID."
    (let [conn (new-db nil)]
      (is (thrown? ExceptionInfo
                   #"Verification UUID does not exist"
                   (api/verify-email conn (str (d/squuid))))))))

(deftest verify-uuid-already-verified
  (testing "Verification is already verified. Should throw exception for invalid UUID."
    (let [db-user (f/user->db-user user)
          db-verification (f/->db-email-verification db-user :verification.status/verified)
          uuid (str (:verification/uuid db-verification))
          conn (new-db [db-user
                        db-verification])]
      (is (thrown? ExceptionInfo
                   #"Invalid verification UUID"
                   (api/verify-email conn uuid))))))

(deftest verify-uuid-expired
  (testing "Verification is already verified. Should throw exception for invalid UUID."
    (let [db-user (f/user->db-user user)
          db-verification (f/->db-email-verification db-user :verification.status/expired)
          uuid (str (:verification/uuid db-verification))
          conn (new-db [db-user
                        db-verification])]
      (is (thrown? ExceptionInfo
                   #"Invalid verification UUID"
                   (api/verify-email conn uuid))))))

(deftest verify-more-than-15-minutes-ago-created-uuid
  (testing "Verification was created more than 15 minutes ago. Should be set to expired and throw exception."
    (let [db-user (f/user->db-user user)
          db-verification (f/->db-email-verification db-user :verification.status/pending)
          expired-verification (assoc db-verification :verification/created-at (c/to-long (t/ago (t/minutes 30))))
          uuid (str (:verification/uuid db-verification))
          conn (new-db [db-user
                        expired-verification])]
      (is (thrown? ExceptionInfo
                   #"Expired verification UUID"
                   (api/verify-email conn uuid)))
      ; Make sure that status of the verification is set to expired.
      (is (= (:db/id (:verification/status (p/verification (d/db conn) '[*] uuid)))
             (d/entid (d/db conn) :verification.status/expired))))))
