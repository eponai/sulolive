(ns flipmunks.budget.auth
  (:require [cemerick.friend.credentials :as creds]
            [cemerick.friend.workflows :as workflows]))

(defn user->creds [user]
  {:identity (:db/id user)
   :username (:user/email user)
   :password (:user/enc-password user)
   :roles #{::user}})

(defn cred-fn [user-fn params]
  (creds/bcrypt-credential-fn user-fn params))

(defn form [& data]
  (apply workflows/interactive-form data))