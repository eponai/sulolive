(ns eponai.server.api-test
  (:require [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.test :refer :all]
            [datomic.api :as d]
            [eponai.server.datomic.pull :as p]
            [eponai.server.api :as api]
            [eponai.server.datomic.format :as f]
            [eponai.server.test-util :refer [new-db user-email]])
  (:import (clojure.lang ExceptionInfo)))

(def schema (read-string (slurp "resources/private/datomic-schema.edn")))

;;;; ------ api/verify-email tests ---------

; Success case
(deftest verification-pending-and-not-expired
  (testing "Verification is pending, and was created less than 15 minutes ago. Should set status to :verification.status/verified"
    (let [db-user (f/user->db-user user-email)
          db-verification (f/->db-email-verification db-user :verification.status/pending)
          uuid (str (:verification/uuid db-verification))
          conn (new-db [db-user
                        db-verification])
          _ (api/verify-email conn uuid)]
      (is (=  (:verification/status (p/verification (d/db conn) uuid))
              :verification.status/verified)))))

; Failure cases
(deftest verify-uuid-does-not-exist
  (testing "Verification UUID does not exist. Should throw exception for invalid UUID."
    (let [conn (new-db)]
      (is (thrown-with-msg? ExceptionInfo
                            #"Verification UUID does not exist"
                            (api/verify-email conn (str (d/squuid))))))))

(deftest verify-uuid-already-verified
  (testing "Verification is already verified. Should throw exception for invalid UUID."
    (let [db-user (f/user->db-user user-email)
          db-verification (f/->db-email-verification db-user :verification.status/verified)
          uuid (str (:verification/uuid db-verification))
          conn (new-db [db-user
                        db-verification])]
      (is (thrown-with-msg? ExceptionInfo
                            #"Invalid verification UUID"
                            (api/verify-email conn uuid))))))

(deftest verify-uuid-expired
  (testing "Verification is already verified. Should throw exception for invalid UUID."
    (let [db-user (f/user->db-user user-email)
          db-verification (f/->db-email-verification db-user :verification.status/expired)
          uuid (str (:verification/uuid db-verification))
          conn (new-db [db-user
                        db-verification])]
      (is (thrown-with-msg? ExceptionInfo
                   #"Invalid verification UUID"
                   (api/verify-email conn uuid))))))

(deftest verify-more-than-15-minutes-ago-created-uuid
  (testing "Verification was created more than 15 minutes ago. Should be set to expired and throw exception."
    (let [db-user (f/user->db-user user-email)
          db-verification (f/->db-email-verification db-user :verification.status/pending)
          expired-verification (assoc db-verification :verification/created-at (c/to-long (t/ago (t/minutes 30))))
          uuid (str (:verification/uuid db-verification))
          conn (new-db [db-user
                        expired-verification])]
      (is (thrown-with-msg? ExceptionInfo
                            #"Expired verification UUID"
                            (api/verify-email conn uuid)))
      ; Make sure that status of the verification is set to expired.
      (is (= (:verification/status (p/verification (d/db conn) uuid))
             :verification.status/expired)))))

;;;; ------ api/activate-account tests ---------

(deftest activate-account-with-verified-email
  (testing "Email is verified for the user, account should be activated."
    (let [db-user (f/user->db-user user-email)
          db-verification (f/->db-email-verification db-user :verification.status/verified)
          conn (new-db [db-user
                        db-verification])
          _ (api/activate-account conn (str (:user/uuid db-user)) user-email)
          user (p/user (d/db conn) user-email)]
      (is (= (:user/status (d/entity (d/db conn) (:db/id user)))
             :user.status/activated)))))

(deftest activate-account-email-already-in-use
  (testing "Email is already in use, should throw exception"
    (let [other-user (f/user->db-user user-email)
          new-user (f/user->db-user "new@email.com")
          verification (f/->db-email-verification new-user :verification.status/verified)
          conn (new-db [other-user
                        new-user
                        verification])]

      (is (thrown-with-msg? ExceptionInfo
                            #"Email already in use"
                            (api/activate-account conn (str (:user/uuid new-user)) user-email))))))