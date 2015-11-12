(ns flipmunks.budget.auth
  (:require [cemerick.friend.credentials :as creds]
            [cemerick.friend.workflows :as workflows]
            [flipmunks.budget.http :as h]
            [postal.core :as email]))

(defn not-found [email]
  (ex-info "Could not find user in db." {:cause   ::authentication-error
                                         :status  ::h/unathorized
                                         :data    {:email email}
                                         :message "Wrong email or password."}))

(defn user->creds
  "Get authentication map from a user entity."
  [user verifications]
  {:identity      (:db/id user)
   :username      (:user/email user)
   :password      (:user/enc-password user)
   :roles         #{::user}
   :verifications verifications})

(defn cred-fn
  "Credential function, passed in a user-fn to load the user credentials from the
  db to compare credentials to the params submitted to log in."
  [user-fn params]
  (when-let [auth-map (creds/bcrypt-credential-fn user-fn params)]
    (if (seq (:verifications auth-map))
      auth-map
      (throw (ex-info "Email verification pending." {:cause   ::verification-error
                                                     :status  ::h/unathorized
                                                     :data    {:email (:user/email (params :username))}
                                                     :message "Email verification pending"})))))

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

(defn send-email-verification [email uuid]
  (let [link (str "localhost:3000/verify/" uuid)]
    (println "email: " email " with link: " link)))
  ;(email/send-message (c/config :smtp) {:from "info@gmail.com" :to "dianagren@gmail.com" :body (str "Email: " email "Click this link: " (verification :verification/uuid))}))