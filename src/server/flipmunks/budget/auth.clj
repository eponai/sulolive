(ns flipmunks.budget.auth
  (:require [cemerick.friend.credentials :as creds]))

(defn cred-fn [user-fn params]
  (creds/bcrypt-credential-fn user-fn params))