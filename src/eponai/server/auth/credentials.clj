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
  (cond
    uuid
    (try
      (let [user (d/entity (d/db conn) (:db/id (api/verify-email conn uuid)))]
        (if (= (:user/status user)
               :user.status/activated)
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
      (let [db-user (d/entity db (-> fb-user
                                     :fb-user/user
                                     :db/id))]
        (if (= (:user/status db-user)
               :user.status/activated)
          (auth-map-for-db-user db-user)
          (throw (ex-info "User not activated."
                          {:new-user db-user}))))
      ; If we don't have a facebook user in the DB, check if there's an accout with a matching email.
      (not fb-user)
      (let [{:keys [email]} (fb-info-fn user_id access_token)]
        ; Create a new fb-user in the DB with the Facebook account trying to log in,
        ; connect the new fb-user to a user account with the same email in the database (if there is one).
        (println "Creating new fb-user: " user_id)

        (let [db-after-link (:db-after (t/link-fb-user conn user_id access_token email))
              fb-user (p/fb-user db-after-link user_id)
              user (d/entity db-after-link (-> fb-user
                                               :fb-user/user
                                               :db/id))]
          (if (= (:user/status user)
                 :user.status/activated)
            ; If there's already a user account using the same email, it's now connected and we can login
            (auth-map-for-db-user user)
            ; If there's no user account with this email, we need to prompt the user to create a new account.
            (throw (ex-info "User not activated."
                            {:new-user user}))))))))

(defmethod auth-map :create-account
  [conn {:keys [user-uuid user-email] :as params}]
  (println "ParamsL " params)
  (try
    (let [user (api/activate-account conn user-uuid user-email)]
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
