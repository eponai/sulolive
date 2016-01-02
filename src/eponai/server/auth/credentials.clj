(ns eponai.server.auth.credentials
  (:require [eponai.server.datomic.pull :as p]
            [eponai.server.http :as h]
            [cemerick.friend.credentials :as creds]))

(defn- user-not-found [email]
  (ex-info "Could not find user in db."
           {:cause   ::authentication-error
            :status  ::h/unathorized
            :data    {:email email}
            :message "Wrong email or password."}))

(defn- user-creds
  "Get user credentials for the specified email in the db. Returns nil if user does not exist.

  Throws ExceptionInfo if the user has not verified their email."
  [db email]
  (when-let [db-user (p/user db email)]
    (let [password (p/password db db-user)
          verifications (p/verifications db db-user :user/email :verification.status/verified)]
      {:identity      (:db/id db-user)
       :username      (:user/email db-user)
       :password      (:password/bcrypt password)
       :roles         #{::user}
       :verifications verifications})))

(defn add-bcrypt
  "Add :bcrypt key to the specified map hashingn the key k in that same map."
  [m k]
  (assoc m :bcrypt (creds/hash-bcrypt (k m))))

(defn password-credential-fn
  "Credential function, passed in a user-fn to load the user credentials from the
  db to compare credentials to the params submitted to log in."
  [db params]
  (when-let [auth-map (creds/bcrypt-credential-fn
                        #(user-creds db %)
                        params)]
    (if (seq (:verifications auth-map))
      (dissoc auth-map :verifications)
      (throw (ex-info "Email verification pending."
                      {:cause   ::verification-error
                       :status  ::h/unathorized
                       :data    {:email (:user/email (params :username))}
                       :message "Email verification pending"})))))