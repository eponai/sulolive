(ns eponai.server.api-test
  (:require
    [clj-time.core :as t]
    [clj-time.coerce :as c]
    [clojure.test :refer :all]
    [datomic.api :as d]
    [eponai.common.database :as db]
    [eponai.server.api :as api]
    [eponai.server.datomic.format :as f]
    [eponai.server.stripe-test :as stripe-test]
    [eponai.server.test-util :refer [new-db user-email]]
    [eponai.common.format :as common.format]
    [clojure.core.async :as async])
  (:import (clojure.lang ExceptionInfo)))

;;;; ------ api/verify-email tests ---------

; Success case
(deftest verification-pending-and-not-expired
  (testing "Verification is pending, and was created less than 15 minutes ago. Should set status to :verification.status/verified"
    (let [{:keys [verification] :as account} (f/user-account-map user-email)
          uuid (:verification/uuid verification)
          conn (new-db (vals account))
          _ (api/verify-email conn (str uuid))]
      (is (= (:verification/status (db/lookup-entity (d/db conn) [:verification/uuid uuid]))
             :verification.status/verified)))))

; Failure cases
(deftest verify-uuid-does-not-exist
  (testing "Verification UUID does not exist. Should throw exception for invalid UUID."
    (let [conn (new-db)]
      (is (thrown-with-msg? ExceptionInfo
                            #":illegal-argument"
                            (api/verify-email conn (str (d/squuid))))))))

(deftest verify-uuid-already-verified
  (testing "Verification is already verified. Should throw exception for invalid UUID."
    (let [{:keys [verification] :as account} (f/user-account-map user-email
                                                                 {:verification/status :verification.status/verified})
          uuid (str (:verification/uuid verification))
          conn (new-db (vals account))]
      (is (thrown-with-msg? ExceptionInfo
                            #":verification-invalid"
                            (api/verify-email conn uuid))))))

(deftest verify-uuid-expired
  (testing "Verification is already verified. Should throw exception for invalid UUID."
    (let [{:keys [verification] :as account} (f/user-account-map user-email
                                                                 {:verification/status :verification.status/expired})
          uuid (str (:verification/uuid verification))
          conn (new-db (vals account))]
      (is (thrown-with-msg? ExceptionInfo
                   #":verification-invalid"
                   (api/verify-email conn uuid))))))

(deftest verify-more-than-15-minutes-ago-created-uuid
  (testing "Verification was created more than 15 minutes ago. Should be set to expired and throw exception."
    (let [half-hour-ago (c/to-long (t/ago (t/minutes 30)))
          {:keys [verification] :as account} (f/user-account-map user-email
                                                                 {:verification/created-at half-hour-ago})
          uuid (:verification/uuid verification)
          conn (new-db (vals account))]
      (is (thrown-with-msg? ExceptionInfo
                            #":verification-expired"
                            (api/verify-email conn (str uuid))))
      ; Make sure that status of the verification is set to expired.
      (is (= (:verification/status (db/lookup-entity (d/db conn) [:verification/uuid uuid]))
             :verification.status/expired)))))

;;;; ------ api/activate-account tests ---------

(deftest activate-account-with-verified-email
  (testing "Email is verified for the user, account should be activated."
    (let [{:keys [user] :as account} (f/user-account-map user-email
                                                         {:verification/status :verification.status/verified})
          conn (new-db (vals account))
          _ (api/activate-account conn (str (:user/uuid user)) user-email)
          user (db/lookup-entity (d/db conn) [:user/email user-email])]
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
                            #":duplicate-email"
                            (api/activate-account conn (str (:user/uuid new-user)) (:user/email existing-user)))))))

(deftest activate-account-no-previous-email
  (testing "User had no email when signin up with Facebook, no trying to activate their account and add one."
    (let [{:keys [user] :as account} (f/user-account-map nil)
          conn (new-db (vals account))]

      (is (thrown-with-msg? ExceptionInfo
                            #":unverified-email"
                            (api/activate-account conn (str (:user/uuid user)) "email")))
      (is (some? (db/lookup-entity (d/db conn) [:user/email "email"]))))))

;;;; --------- Share project tests -----------------------

(comment
  ;; TODO:
  "Failing test because of arity. Want to fix this later?"
  (deftest share-project-existing-user-invited
   (testing "An existing user account is invited to share a project. Should add the user to the project and send email"
     (let [invitee "share@email.com"
           {:keys [user] :as account-inviter} (f/user-account-map user-email)
           account-invitee (f/user-account-map invitee)
           project (common.format/project (:db/id user))
           conn (new-db (conj (concat (vals account-inviter)
                                      (vals account-invitee))
                              project))
           result (api/share-project conn (:project/uuid project) invitee)
           {:keys [project/_users]} (db/pull (d/db conn) [{:project/_users [:project/uuid]}] [:user/email invitee])]
       (is (= (:status result) (:user/status user)))
       (is (= 2 (count _users)))
       (is (= (:verification/status (async/<!! (get result :email-chan))) :verification.status/pending))))))

(comment
  ;; TODO:
  "Failing test because of arity. Want to fix this later?"
  (deftest share-project-new-user-email-invited
   (testing "An new user account is invited to share a project. Should add the user to the project and send email"
     (let [invitee "share@email.com"
           {:keys [user] :as account-inviter} (f/user-account-map user-email)
           project (common.format/project (:db/id user))
           conn (new-db (conj (vals account-inviter)
                              project))
           result (api/share-project conn (:project/uuid project) invitee)
           {:keys [project/_users]} (db/pull (d/db conn) [{:project/_users [:project/uuid]}] [:user/email invitee])]
       (is (= (:status result) (:user/status user)))
       (is (= 1 (count _users)))
       (is (= (:verification/status (async/<!! (get result :email-chan))) :verification.status/pending))))))

(comment
  ;; TODO:
  "Failing test because of arity. Want to fix this later?"
  (deftest share-project-user-already-sharing
   (testing "A user is invited to share a project, but is already sharing. Throw exception."
     (let [{:keys [user] :as account-inviter} (f/user-account-map user-email)
           project (common.format/project (:db/id user))
           conn (new-db (conj (vals account-inviter)
                              project))]
       (is (thrown-with-msg? ExceptionInfo
                             #":duplicate-project-shares"
                             (api/share-project conn (:project/uuid project) user-email)))
       (let [{:keys [project/_users]} (db/pull (d/db conn) [{:project/_users [:project/uuid]}] [:user/email user-email])]
         (is (= 2 (count _users))))))))


;;;;;; ---------------------------- Stripe tests -----------------------------

(defn test-stripe-read
  [status]
  (fn [k params]
    (stripe-test/test-stripe status k params)))

(def stripe-params {:customer-id     "some-customer"
                    :subscription-id "some-subscription"
                    :plan            "test-plan"
                    :token           {:email user-email
                                      :id    "test-id"}})
(deftest new-user-starts-trial
  (testing "A user with no prior subscriptions starts a trial. Should create customer and subscription."
    (let [{:keys [user] :as a} (f/user-account-map user-email)
          conn (new-db (vals a))
          _ (api/stripe-trial conn (test-stripe-read :trialing) user)
          customer (db/pull (d/db conn) [{:stripe/user [:user/email]} {:stripe/subscription '[*]}] [:stripe/customer stripe-test/default-customer-id])]
      (is (some? customer))
      (let [sub (:stripe/subscription customer)]
        (is (= (:stripe.subscription/id sub) stripe-test/default-subscription-id))
        (is (= (:stripe.subscription/status sub) :trialing))
        (is (= (get-in customer [:stripe/user :user/email]) user-email))))))

(deftest trialing-user-adds-card
  (testing "A user on trial adds card. Should activate subscription."
    (let [{:keys [user] :as account} (f/user-account-map user-email)
          {:keys [customer-id
                  subscription-id]} stripe-params
          ; Create stripe entity for the user account
          stripe (f/stripe-account (:db/id user) {:stripe/customer customer-id
                                                  :stripe/subscription (stripe-test/new-db-subscription subscription-id :trialing)})
          ; Setup DB with the user and their trial Stripe account
          conn (new-db (conj (vals account)
                             stripe))
          ; Call the API entry point for updating the user's card on file.
          _ (api/stripe-update-card conn (test-stripe-read :active) stripe {:token (:token stripe-params)})

          ; Get new customer entity from DB after having update the customer card. Status should have been set to active at this point.
          customer (db/pull (d/db conn) [{:stripe/subscription '[*]}] [:stripe/customer customer-id])]
      (is (= (get-in customer [:stripe/subscription :stripe.subscription/status]) :active)))))

;(deftest trial-user-updates-subscription
;  (testing "A user with a trial upgrades their subscription. Should update existing subscription."
;    (let [{:keys [user] :as account} (f/user-account-map user-email)
;          {:keys [customer-id
;                  subscription-id]} stripe-params
;          subscription (stripe-test/subscription subscription-id :trialing)
;          stripe (f/stripe-account (:db/id user) {:stripe/customer customer-id
;                                                  :stripe/subscription subscription})
;          conn (new-db (conj (vals account)
;                             stripe))
;          _ (api/stripe-subscribe conn (test-stripe-read :active) stripe stripe-params)
;          customer (p/pull (d/db conn) [{:stripe/user [:user/email]} {:stripe/subscription '[*]}] [:stripe/customer customer-id])]
;      (is (some? customer))
;      (let [sub (:stripe/subscription customer)]
;        (is (= (:stripe.subscription/id sub) subscription-id))
;        (is (= (:stripe.subscription/status sub) :active))
;        (is (= (get-in customer [:stripe/user :user/email]) user-email))))))

;(deftest user-cancels-subscription
;  (testing "A user with subscription cancels, subscription should be removed from db."
;    (let [{:keys [user] :as account} (f/user-account-map user-email)
;          {:keys [customer-id
;                  subscription-id]} stripe-params
;          subscription (stripe-test/subscription subscription-id :active)
;          stripe (f/stripe-account (:db/id user) {:stripe/customer customer-id
;                                                  :stripe/subscription subscription})
;          conn (new-db (conj (vals account)
;                             stripe))
;          _ (api/stripe-cancel {:state         conn
;                                :stripe-fn     (test-stripe-read :active)} stripe)
;          customer (p/pull (d/db conn) [{:stripe/user [:user/email]} {:stripe/subscription '[*]}] [:stripe/customer customer-id])]
;      (is (some? customer))
;      (is (nil? (:stripe/subscription customer))))))

;(deftest user-subscribes-after-having-previously-canceled
;  (testing "User that has previously canceled subscribes. Has existing stripe customer and should create a new subscription"
;    (let [{:keys [user] :as account} (f/user-account-map user-email)
;          {:keys [customer-id]} stripe-params
;          stripe (f/stripe-account (:db/id user) {:stripe/customer customer-id})
;          _ (prn "Got stripe account: " stripe)
;          conn (new-db (conj (vals account)
;                             stripe))
;          _ (api/stripe-subscribe conn (test-stripe-read :active) stripe stripe-params)
;          customer (p/pull (d/db conn) [{:stripe/user [:user/email]} {:stripe/subscription '[*]}] [:stripe/customer customer-id])]
;      (is (some? customer))
;      (is (= (get-in customer [:stripe/subscription :stripe.subscription/id]) stripe-test/default-subscription-id)))))

;(deftest user-cancels-after-having-canceled
;  (testing "User tries to cancel again after already having canceled. Should throw exception."
;    (let [{:keys [user] :as account} (f/user-account-map user-email)
;          {:keys [customer-id]} stripe-params
;          stripe (f/stripe-account (:db/id user) {:stripe/customer customer-id})
;          _ (prn "Got stripe account: " stripe)
;          conn (new-db (conj (vals account)
;                             stripe))]
;      (is (thrown-with-msg? ExceptionInfo
;                            #"missing-required-fields"
;                            (api/stripe-cancel {:state         conn
;                                                :stripe-fn     (test-stripe-read :active)} stripe))))))

(deftest user-starts-trial-after-already-having-a-customer-account
  (testing "User starts a trial when they already have been subscribed. Throw exception."
    (let [{:keys [user] :as account} (f/user-account-map user-email)
          {:keys [customer-id]} stripe-params
          stripe (f/stripe-account (:db/id user) {:stripe/customer customer-id})
          _ (prn "Got stripe account: " stripe)
          conn (new-db (conj (vals account)
                             stripe))
          db-user (db/pull (d/db conn) [:user/email :stripe/_user] [:user/email user-email])]
      (is (thrown-with-msg? ExceptionInfo
                            #":illegal-argument"
                            (api/stripe-trial conn (test-stripe-read :trialing) db-user))))))

;(deftest user-subscribes-without-trial
;  (testing "User subscribes without doing a trial first. Create customer and subscription."
;    (let [{:keys [user] :as a} (f/user-account-map user-email)
;          conn (new-db (vals a))
;          _ (api/stripe-subscribe conn (test-stripe-read :active) nil stripe-params)
;          customer (p/pull (d/db conn) [{:stripe/user [:user/email]} {:stripe/subscription '[*]}] [:stripe/customer stripe-test/default-customer-id])]
;      (is (some? customer))
;      (let [sub (:stripe/subscription customer)]
;        (is (= (:stripe.subscription/id sub) stripe-test/default-subscription-id))
;        (is (= (:stripe.subscription/status sub) :active))
;        (is (= (get-in customer [:stripe/user :user/email]) user-email))))))