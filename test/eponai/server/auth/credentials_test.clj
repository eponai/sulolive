(ns eponai.server.auth.credentials-test
  (:require [cemerick.friend :as friend]
            [clojure.test :refer :all]
            [datomic.api :as d]
            [eponai.server.datomic.format :as f]
            [eponai.server.auth.credentials :as a]
            [eponai.common.database :as db]
            [eponai.server.test-util :refer [new-db]]
            [eponai.server.api :as api])
  (:import (clojure.lang ExceptionInfo)))

(defn test-fb-info
  ([]
   (test-fb-info "fb-user-id" "access-token"))
  ([id _]
   {:name  "Facebook name"
    :email "email@email.com"
    :id    id}))

(def email "email@email.com")

(defn creds-input
  ([user-id]
    (creds-input user-id test-fb-info))
  ([user-id fb-info-fn]
   (with-meta {:user_id      user-id
               :access_token "access-token"
               :fb-info-fn   fb-info-fn}
              {::friend/workflow :facebook})))

;; -------- FB Credential function tests

(deftest fb-user-and-activated-user-account-with-email-exist
  (testing "Fb user exists and is connected to an existing activated user account, should log in."
    (let [{:keys [email id]} (test-fb-info)
          {:keys [user] :as account} (f/user-account-map email
                                                         {:user/status :user.status/active
                                                          :fb-user/id id
                                                          :fb-user/token "access-token"})
          conn (new-db (vals account))
          credential-fn (a/credential-fn conn)
          user-record (credential-fn (creds-input id))]

      ; We're authenticated.
      (is (= user-record
             (a/auth-map-for-db-user (db/lookup-entity (d/db conn) [:user/email (:user/email user)]) a/user-roles-active))))))

(deftest fb-user-exists-but-no-user-account-with-matching-email
  (testing "FB account exists, but a matching user account does not. Should prompt user to create an account
  (can happen if the FB account does not have an email)."
    (let [{:keys [_ id]} (test-fb-info)
          {:keys [user fb-user]} (f/user-account-map nil
                                                     {:fb-user/id id
                                                      :fb-user/token "access-token"})
          conn (new-db [user fb-user])
          credential-fn (a/credential-fn conn)
          user-record (credential-fn
                        (creds-input id))
          db-user (:fb-user/user (db/lookup-entity (d/db conn) [:fb-user/id id]))]

      (is (= user-record
             (a/auth-map-for-db-user db-user a/user-roles-inactive))))))

(deftest fb-user-not-exist-but-activated-user-account-with-email-does
  (testing "The FB account does not exist, but a user account with the same email does.
  Should create a new fb-user and link to the existing account."
    (let [{:keys [email id]} (test-fb-info)
          user (f/user email {:user/status :user.status/active})
          conn (new-db [user])
          credential-fn (a/credential-fn conn)
          user-record (credential-fn (creds-input id))
          ; Entities added to the DB
          db-user (db/lookup-entity (d/db conn) [:user/email email])
          fb-user (db/lookup-entity (d/db conn) [:fb-user/id id])]

      ; We're authenticated
      (is (= user-record
             (a/auth-map-for-db-user db-user a/user-roles-active)))
      ;The FB user is added to the DB and linked to the db-user
      (is fb-user)
      ; The created FB user is linked to the user account with the same email.
      (is (= (:db/id (:fb-user/user fb-user))
             (:db/id db-user))))))

(deftest new-fb-user-has-email-and-user-account-does-not-exist
  (testing "Neither FB user (with email) or user account with the mathing email exists,
  create a new FB user and link to a new account."
    (let [{:keys [email id]} (test-fb-info)
          conn (new-db)
          credential-fn (a/credential-fn conn)
          user-record (credential-fn (creds-input id))]

      (let [{:keys [fb-user/user]} (db/lookup-entity (d/db conn) [:fb-user/id id])]
        ;TODO we might want to automatically create an ccount here and let the user login immediately?
        (is (= user-record
               (a/auth-map-for-db-user (db/lookup-entity (d/db conn) (:db/id user)) a/user-roles-active)))
        (is (= (:user/email (d/entity (d/db conn) (:db/id user)))
               email))))))

(deftest fb-user-without-email-does-not-exist
  (testing "Fb user does not have an email and is trying to sign up. New fb user linked to a new user should be created.
  Should be prompted to activate account."
    (let [fb-info-fn (fn [_ _]
                       (dissoc (test-fb-info) :email))
          {:keys [id]} (fb-info-fn nil nil)
          conn (new-db)
          credential-fn (a/credential-fn conn)
          user-record (credential-fn (creds-input id fb-info-fn))]

      (let [fb-user (db/lookup-entity (d/db conn) [:fb-user/id id])]
        (is fb-user)
        (is (:fb-user/user fb-user))
        (is (= user-record
               (a/auth-map-for-db-user (:fb-user/user fb-user) a/user-roles-inactive)))))))


;; ------ User verify email credential function tests.
;; Covering everything relevat for :form credential-fn.
;; More detailed cases are covered for api/verify-email in api-test.clj

(deftest user-verifies-email-with-already-activated-account
  (testing "User verifies their email and is already activated, return auth map"
    (let [{:keys [verification] :as account} (f/user-account-map email
                                                                 {:user/status :user.status/active
                                                                  :verification/status :verification.status/pending})
          conn (new-db (vals account))
          credential-fn (a/credential-fn conn)]

      (is (= (credential-fn
               (with-meta {:uuid (str (:verification/uuid verification))}
                          {::friend/workflow :form}))
             (a/auth-map-for-db-user (db/lookup-entity (d/db conn) [:user/email email]) a/user-roles-active))))))

; Failure cases
(deftest user-verifies-email-with-account-not-activated
  (testing "User is verified but not activated, should activate the user, since we already have an email from the email verification flow. Should be subscribed to Stripe trial as well."
    (let [{:keys [verification] :as account} (f/user-account-map email)
          conn (new-db (vals account))
          credential-fn (a/credential-fn conn)
          stripe-fn (fn [_ _]
                      {:stripe/customer     "cus-id"
                       :stripe/subscription {:stripe.subscription/id "sub-id"}})
          user-record (credential-fn
                        (with-meta {:uuid (str (:verification/uuid verification))
                                    :stripe-fn stripe-fn}
                                   {::friend/workflow :form}))
          stripe-cus (db/lookup-entity (d/db conn) [:stripe/customer "cus-id"])]

      (is (= user-record
             (a/auth-map-for-db-user (db/lookup-entity (d/db conn) [:user/email email]) a/user-roles-active)))
      (is (and (some? stripe-cus)
               (some? (:stripe/subscription stripe-cus)))))))

(deftest user-verifies-nil-uuid
  (testing "User tries to verify a nil UUID. Throw exception"
    (let [conn (new-db)
          credential-fn (a/credential-fn conn)]
      (is (thrown-with-msg? ExceptionInfo
                            #"missing-required-fields"
                            (credential-fn
                              (with-meta {:invalid :data}
                                         {::friend/workflow :form})))))))

;; ------ User activate account credential function tests.
;; Covering everything relevat for :form credential-fn.
;; More detailed cases are covered for api/activate-account in api-test.clj

;(deftest user-activates-account-with-verified-email
;  (testing "User activates account with their email verified."
;    (let [{:keys [user] :as account} (f/user-account-map email
;                                                          {:verification/status :verification.status/verified})
;          conn (new-db (vals account))
;          credential-fn (a/credential-fn conn)
;          stripe-fn (fn [_ _]
;                      {:stripe/customer     "cus-id"
;                       :stripe/subscription {:stripe.subscription/id "sub-id"}})
;          activated-auth (credential-fn (with-meta {:user-uuid  (str (:user/uuid user))
;                                                    :user-email (:user/email user)
;                                                    :stripe-fn stripe-fn}
;                                                   {::friend/workflow :activate-account}))
;          stripe-cus (p/lookup-entity (d/db conn) [:stripe/customer "cus-id"])]
;      (is (= activated-auth
;             (a/auth-map-for-db-user (p/lookup-entity (d/db conn) [:user/email email]) a/user-roles-active)))
;      (is (and (some? stripe-cus)
;               (some? (:stripe/subscription stripe-cus)))))))

(deftest user-activates-account-already-activated
  (testing "User activates account which as already activated (could bypass trial period).
  Should not reset activation time."
    (let [{:keys [user verification]} (f/user-account-map email
                                                          {:verification/status :verification.status/verified
                                                           :user/status :user.status/active})
          conn (new-db [user
                        verification])
          credential-fn (a/credential-fn conn)
          stripe-fn (fn [_ _]
                      {:stripe/customer     "cus-id"
                       :stripe/subscription {:stripe.subscription/id "sub-id"}})
          activated-auth (credential-fn (with-meta {:user-uuid  (str (:user/uuid user))
                                                    :user-email (:user/email user)
                                                    :stripe-fn stripe-fn}
                                                   {::friend/workflow :activate-account}))
          stripe-cus (db/lookup-entity (d/db conn) [:stripe/customer "cus-id"])]
      (is (= activated-auth
             (a/auth-map-for-db-user (db/lookup-entity (d/db conn) [:user/email email]) a/user-roles-active)))
      (is (nil? stripe-cus)))))

(deftest user-activates-account-with-invalid-input
  (testing "Invalid input is coming in to the credential fn, should throw exception."
    (let [conn (new-db)
          credential-fn (a/credential-fn conn)]
      (is (thrown-with-msg? ExceptionInfo
                            #"missing-required-fields"
                            (credential-fn
                              (with-meta {:invalid :data}
                                         {::friend/workflow :activate-account})))))))