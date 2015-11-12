(ns flipmunks.budget.auth
  (:require [cemerick.friend.credentials :as creds]
            [cemerick.friend.workflows :as workflows]
            [flipmunks.budget.http :as h]))

(defn user->creds
  "Get authentication map from a user entity."
  [user verifications]
  (if (seq verifications)
    {:identity (:db/id user)
     :username (:user/email user)
     :password (:user/enc-password user)
     :roles    #{::user}}
    (throw (ex-info "Email verification pending." {:cause   ::verification-error
                                                   :status  ::h/unathorized
                                                   :data    {:email (:user/email user)}
                                                   :message "Email verification pending"}))))

(defn cred-fn
  "Credential function, passed in a user-fn to load the user credentials from the
  db to compare credentials to the params submitted to log in."
  [user-fn params]
  (creds/bcrypt-credential-fn user-fn params))

(defn form
  "Form workflow"
  [& data]
  (apply workflows/interactive-form data))

(defn new-signup
  "Verify new user input and return a user entity if valid."
  [{:keys [params request-method]}]
  (if (= request-method :post)
    {:user/email        (params :username)
     :user/enc-password (creds/hash-bcrypt (params :password))}
    (throw (ex-info "Invalid request method" {:cause   ::request-error
                                              :status  ::h/unprocessable-entity
                                              :data    {:request-method request-method}
                                              :message "Invalid request method"}))))