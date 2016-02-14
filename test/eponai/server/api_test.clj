(ns eponai.server.api-test
  (:require [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.test :refer :all]
            [datomic.api :as d]
            [eponai.common.database.pull :as p]
            [eponai.server.api :as api]
            [eponai.server.datomic.format :as f]
            [eponai.server.test-util :refer [new-db user-email schema]])
  (:import (clojure.lang ExceptionInfo)))

;;;; ------ api/verify-email tests ---------

; Success case
(deftest verification-pending-and-not-expired
  (testing "Verification is pending, and was created less than 15 minutes ago. Should set status to :verification.status/verified"
    (let [{:keys [verification] :as account} (f/user-account-map user-email)
          uuid (:verification/uuid verification)
          conn (new-db (vals account))
          _ (api/verify-email conn (str uuid))]
      (is (=  (:verification/status (p/lookup-entity (d/db conn) [:verification/uuid uuid]))
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
    (let [{:keys [verification] :as account} (f/user-account-map user-email
                                                                 {:verification/status :verification.status/verified})
          uuid (str (:verification/uuid verification))
          conn (new-db (vals account))]
      (is (thrown-with-msg? ExceptionInfo
                            #"Invalid verification UUID"
                            (api/verify-email conn uuid))))))

(deftest verify-uuid-expired
  (testing "Verification is already verified. Should throw exception for invalid UUID."
    (let [{:keys [verification] :as account} (f/user-account-map user-email
                                                                 {:verification/status :verification.status/expired})
          uuid (str (:verification/uuid verification))
          conn (new-db (vals account))]
      (is (thrown-with-msg? ExceptionInfo
                   #"Invalid verification UUID"
                   (api/verify-email conn uuid))))))

(deftest verify-more-than-15-minutes-ago-created-uuid
  (testing "Verification was created more than 15 minutes ago. Should be set to expired and throw exception."
    (let [half-hour-ago (c/to-long (t/ago (t/minutes 30)))
          {:keys [verification] :as account} (f/user-account-map user-email
                                                                 {:verification/created-at half-hour-ago})
          uuid (:verification/uuid verification)
          conn (new-db (vals account))]
      (is (thrown-with-msg? ExceptionInfo
                            #"Expired verification UUID"
                            (api/verify-email conn (str uuid))))
      ; Make sure that status of the verification is set to expired.
      (is (= (:verification/status (p/lookup-entity (d/db conn) [:verification/uuid uuid]))
             :verification.status/expired)))))

;;;; ------ api/activate-account tests ---------

(deftest activate-account-with-verified-email
  (testing "Email is verified for the user, account should be activated."
    (let [{:keys [user] :as account} (f/user-account-map user-email
                                                         {:verification/status :verification.status/verified})
          conn (new-db (vals account))
          _ (api/activate-account conn (str (:user/uuid user)) user-email)
          user (p/lookup-entity (d/db conn) [:user/email user-email])]
      (is (= (:user/status (d/entity (d/db conn) (:db/id user)))
             :user.status/active)))))

(deftest activate-account-email-already-in-use
  (testing "Email is already in use, should throw exception"
    (let [{existing-user :user :as account} (f/user-account-map user-email
                                                    {:verification/status :verification.status/verified})
          {new-user :user} (f/user-account-map "new@email.com")
          conn (new-db (conj (vals account)
                             new-user))]

      (is (thrown-with-msg? ExceptionInfo
                            #"Email already in use"
                            (api/activate-account conn (str (:user/uuid new-user)) (:user/email existing-user)))))))