(ns eponai.server.auth.credentials
  (:require [cemerick.friend :as friend]
            [cemerick.friend.credentials :as creds]
            [datomic.api :as d]
            [eponai.server.datomic.pull :as p]
            [eponai.common.database.pull :as pull]
            [eponai.server.http :as h]
            [eponai.server.datomic.transact :as t]
            [clj-time.coerce :as c]
            [clj-time.core :as time]
            [eponai.server.api :as api]
            [taoensso.timbre :refer [debug error info]])
  (:import (clojure.lang ExceptionInfo)))

; ---- exceptions

(defn ex-invalid-input [input]
  (ex-info "Invalid input."
           {:cause ::authentication-error
            :status ::h/unprocessable-entity
            :data {:input input}}))

(defn ex-user-not-activated [user]
  (ex-info "User not activated."
           {:cause ::authentication-error
            :activate-user user}))

(defn auth-map-for-db-user [user]
  {:identity (:db/id user)
   :username (:user/uuid user)
   :roles    #{::user}})

(defmulti auth-map
          (fn [_ input] (::friend/workflow (meta input))))

(defmethod auth-map :default
  [conn {:keys [uuid] :as params}]
  (if uuid
    (let [user (d/entity (d/db conn) (:db/id (api/verify-email conn uuid)))]
      (if (= (:user/status user)
             :user.status/activated)
        (auth-map-for-db-user user)
        (throw (ex-user-not-activated user))))
    (throw (ex-invalid-input params))))

(defmethod auth-map :facebook
  [conn {:keys [access_token user_id fb-info-fn] :as params}]
  (if (and user_id access_token fb-info-fn)
    (if-let [fb-user (p/fb-user (d/db conn) user_id)]
      (let [db-user (d/entity (d/db conn) (-> fb-user
                                              :fb-user/user
                                              :db/id))]

        ;; Check that the user is activated, if not throw exception.
        (if (= (:user/status db-user)
               :user.status/activated)
          (auth-map-for-db-user db-user)
          (throw (ex-user-not-activated db-user))))

      ; If we don't have a facebook user in the DB, check if there's an accout with a matching email.
      (let [{:keys [email]} (fb-info-fn user_id access_token)]
        (debug "Creating new fb-user: " user_id)
        ;; Linking the FB user to u user account. If a user accunt with the same email exists,
        ;; it will be linked. Otherwise, a new user is created.
        (let [db-after-link (:db-after (t/link-fb-user conn user_id access_token email))
              fb-user (p/fb-user db-after-link user_id)
              user (d/entity db-after-link (-> fb-user
                                               :fb-user/user
                                               :db/id))]
          ;; Again, check that the linked user is activated, and if not we throw an excaption to do so.
          (if (= (:user/status user)
                 :user.status/activated)
            (auth-map-for-db-user user)
            (throw (ex-user-not-activated user))))))
    (throw (ex-invalid-input params))))

(defmethod auth-map :create-account
  [conn {:keys [user-uuid user-email] :as params}]
  (if (and user-uuid user-email)
    (let [user (api/activate-account conn user-uuid user-email)]
      (auth-map-for-db-user user))
    (throw ex-invalid-input)))

(defn credential-fn
  "Create a credential fn with a db to pull user credentials.

  Returned function will dispatc on the ::friend/workflow and return the
  appropriate authentication map for the workflow."
  [conn]
  (fn [input]
    (auth-map conn input)))
