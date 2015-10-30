(ns flipmunks.budget.auth
  (:require [cemerick.friend.credentials :as creds]
            [cemerick.friend.workflows :as workflows]))

(defn cred-fn [user-fn params]
  (creds/bcrypt-credential-fn user-fn params))

(defn form [& data]
  (apply workflows/interactive-form data))