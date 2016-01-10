(ns eponai.server.auth.credentials
  (:require [cemerick.friend :as friend]
            [cemerick.friend.credentials :as creds]
            [datomic.api :as d :refer [db]]
            [eponai.server.datomic.pull :as p]
            [eponai.common.database.pull :as pull]
            [eponai.server.http :as h]
            [eponai.server.datomic.transact :as t]
            [clj-time.coerce :as c]
            [clj-time.core :as time]
            [eponai.server.api :as api])
  (:import (clojure.lang ExceptionInfo)))

(defn- user-not-found [email]
  (ex-info "Could not find user in db."
           {:cause   ::authentication-error
            :status  ::h/unathorized
            :data    {:email email}
            :message "Wrong email or password."}))

(defn auth-map-for-db-user [user]
  {:identity (:db/id user)
   :username (:user/uuid user)
   :roles    #{::user}})

(defmulti auth-map
          (fn [_ input] (::friend/workflow (meta input))))

(defmethod auth-map :default
  [conn {:keys [uuid] :as params}]
  (println "Credential fn for email.")
  (cond
    uuid
    (try
      (let [user (api/verify-email conn uuid)
            user (pull/pull (d/db conn) '[* {:user/status [:db/ident]}] (:db/id user))]
        (prn "User: " user)
        (if (= (:db/id (:user/status user))
               (d/entid (d/db conn) :user.status/activated))
          (auth-map-for-db-user user)
          (throw (ex-info "New user." {:cause ::authentication-error
                                       :new-user user}))))
      (catch ExceptionInfo e
        (println "Exception thrown: " (.getMessage e))
        (throw e)))
    true
    (throw (ex-info "Cannot log in" {:cause ::authentication-error
                                     :status ::h/unprocessable-entity
                                     :data params}))))

(defn- new-user-for-fb-account [user-id]
  {:new-user {:fb user-id}})

(defmethod auth-map :facebook
  [conn {:keys [access_token user_id fb-info-fn]}]
  (let [db (db conn)
        fb-user (p/fb-user db user_id)]
    (cond
      fb-user
      ; If we already have a fb-user in the db, check if it's connected to a user account already
      (let [db-user (d/entity db (-> fb-user
                                     :fb-user/user
                                     :db/id))]
        (println "FB user exits: " (:fb-user/id fb-user))
        (cond
          db-user
          ; If the fb account is already connected to a user account, create an auth map and login.
          (auth-map-for-db-user db-user)

          ; If we do not have a connected account to the FB account, we check for an account that's using the same email.
          (not db-user)
          (let [{:keys [email]} (fb-info-fn user_id access_token)
                db-user (p/user db email)]
            (if db-user
              ; If there's a user account with the same email, connect the FB account to that user, and then login.
              (do
                (println "User account found, connecting fb account..." db-user)
                (t/add conn (:db/id fb-user) :fb-user/user (:db/id db-user))
                (auth-map-for-db-user db-user))
              ; If we don't have a matching user account, we need to prompt the person logging in to create an account.
              (throw (ex-info "No user account found, create a new one."
                              (new-user-for-fb-account user_id)))))))

      ; If we don't have a facebook user in the DB, check if there's an accout with a matching email.
      (not fb-user)
      (let [{:keys [email]} (fb-info-fn user_id access_token)
            db-user (p/user db email)]
        ; Create a new fb-user in the DB with the Facebook account trying to log in,
        ; connect the new fb-user to a user account with the same email in the database (if there is one).
        (println "Creating new fb-user: " user_id)
        (t/new-fb-user conn user_id access_token db-user)
        (if db-user
          ; If there's already a user account using the same email, it's now connected and we can login
          (auth-map-for-db-user db-user)
          ; If there's no user account with this email, we need to prompt the user to create a new account.
          (throw (ex-info "No user account found, create a new one."
                          (new-user-for-fb-account user_id))))))))

(defmethod auth-map :create-account
  [conn {:keys [user-uuid user-email] :as params}]
  (println "ParamsL " params)
  (try
    (let [user (api/create-account conn user-uuid user-email)]
      (auth-map-for-db-user user))
    (catch ExceptionInfo e
      (println "Exception thrown when creating user: " (.getMessage e))
      (throw e))))

(defn credential-fn
  "Create a credential fn with a db to pull user credentials.

  Returned function will dispatc on the ::friend/workflow and return the
  appropriate authentication map for the workflow."
  [conn]
  (fn [input]
    (auth-map conn input)))
