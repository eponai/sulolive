(ns flipmunks.budget.auth
  (:require [cemerick.friend.credentials :as creds]
            [cemerick.friend.workflows :as workflows]
            [clojure.walk :refer [keywordize-keys]]
            [flipmunks.budget.validate :as v]))

(defn user->creds
  "Get authentication map from a user entity."
  [user]
  {:identity (:db/id user)
   :username (:user/email user)
   :password (:user/enc-password user)
   :roles #{::user}})

(defn cred-fn
  "Credential function, passed in a user-fn to load the user credentials from the
  db to compare credentials to the params submitted to log in."
  [user-fn params]
  (creds/bcrypt-credential-fn user-fn params))

(defn form
  "Form workflow"
  [& data]
  (apply workflows/interactive-form data))

(defn signup
  "Verify new user input and return a user entity if valid."
  [{:keys [params] :as request}]
  (when (v/valid-signup? request)
    {:user/email        (params :username)
     :user/enc-password (creds/hash-bcrypt (params :password))}))