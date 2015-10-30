(ns flipmunks.budget.auth
  (:require [cemerick.friend.credentials :as creds]
            [cemerick.friend.workflows :as workflows]
            [clojure.walk :refer [keywordize-keys]]
            [flipmunks.budget.validate :as v])
  (:import (clojure.lang ExceptionInfo)))

(defn user->creds [user]
  {:identity (:db/id user)
   :username (:user/email user)
   :password (:user/enc-password user)
   :roles #{::user}})

(defn cred-fn [user-fn params]
  (creds/bcrypt-credential-fn user-fn params))

(defn form [& data]
  (apply workflows/interactive-form data))

(defn signup [{:keys [params] :as request}]
  (try
    (when (v/valid-signup? request)
      {:user/email        (params :username)
       :user/enc-password (creds/hash-bcrypt (params :password))})
    (catch ExceptionInfo e
      nil)))