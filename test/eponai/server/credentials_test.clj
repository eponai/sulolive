(ns eponai.server.credentials-test
  (:require [cemerick.friend :as friend]
            [clojure.test :refer :all]
            [datomic.api :as d]
            [eponai.server.datomic.format :as f]
            [eponai.server.auth.credentials :as a]
            [eponai.server.datomic.pull :as p])
  (:import (clojure.lang ExceptionInfo)))

(def schema (read-string (slurp "resources/private/datomic-schema.edn")))

(defn test-fb-info
  ([]
   (test-fb-info "fb-user-id" "access-token"))
  ([id _]
   {:name  "Facebook name"
    :email "email@email.com"
    :id    id}))

(defn creds-input
  ([user-id]
    (creds-input user-id test-fb-info))
  ([user-id fb-info-fn]
   (with-meta {:user_id      user-id
               :access_token "access-token"
               :fb-info-fn   fb-info-fn}
              {::friend/workflow :facebook})))

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

;; -------- FB Credential function tests

(deftest fb-user-and-user-account-both-exist
  (testing "Fb user exists and is connected to an existing user account, should log in."
    (let [{:keys [email id]} (test-fb-info)
          db-user (f/user->db-user email)
          fb-user (f/fb-user-db-user id "access-token" db-user)
          conn (new-db [db-user fb-user])
          credential-fn (a/credential-fn conn)
          user-record (credential-fn (creds-input id))]

      ; We're authenticated.
      (is (= user-record
             (a/auth-map-for-db-user (p/user (d/db conn) (:user/email db-user))))))))

(deftest fb-user-exists-but-no-user-account-with-matching-email
  (testing "FB account exists, but a matching user account does not. Should prompt user to create an account
  (can happen if the FB account does not have an email)."
    (let [{:keys [_ id]} (test-fb-info)
          fb-user (f/fb-user-db-user id "access-token" nil)
          conn (new-db [fb-user])
          credential-fn (a/credential-fn conn)]

      (is (thrown? ExceptionInfo
                   #"No user account found, create one"
                   (credential-fn
                     (creds-input id)))))))

(deftest fb-user-not-exists-but-user-account-with-email-does
  (testing "The FB account does not exist, but a user account with the same email does.
  Should create a new fb-user and link to the existing account."
    (let [{:keys [email id]} (test-fb-info)
          conn (new-db [(f/user->db-user email)])
          credential-fn (a/credential-fn conn)
          user-record (credential-fn (creds-input id))
          ; Entities addeed to the DB
          db-user (p/user (d/db conn) email)
          fb-user (p/fb-user (d/db conn) id)]

      ; We're authenticated
      (is (= user-record
             (a/auth-map-for-db-user db-user)))
      ;The FB user is added to the DB and linked to the db-user
      (is fb-user)
      ; The created FB user is linked to the user account with the same email.
      (is (= (:db/id (:fb-user/user fb-user))
             (:db/id db-user))))))

(deftest fb-user-with-email-and-user-account-does-not-exist
  (testing "Neither FB user (with email) or user account with the mathing email exists,
  create a new FB user and link to a new account."
    (let [{:keys [_ id]} (test-fb-info)
          conn (new-db nil)
          credential-fn (a/credential-fn conn)]

      ;TODO we might want to automatically create an ccount here and let the user login immediately?
      (is (thrown? ExceptionInfo
                   #"No user found, create one"
                   (credential-fn (creds-input id)))))))

(deftest fb-user-without-email-does-not-exist
  (testing "Fb user does not have an email and is trying to sign up.
  Should be prompted th create an account with an email."
    (let [fb-info-fn (fn [_ _]
                       (dissoc (test-fb-info) :email))
          {:keys [id]} (fb-info-fn nil nil)
          conn (new-db nil)
          credential-fn (a/credential-fn conn)]

      (is (thrown? ExceptionInfo
                   #"No user found, create one"
                   (credential-fn (creds-input id fb-info-fn)))))))


;; ------ User-email credential function tests.