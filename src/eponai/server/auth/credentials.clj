(ns eponai.server.auth.credentials
  (:require [cemerick.friend :as friend]
            [cemerick.friend.credentials :as creds]
            [datomic.api :refer [db]]
            [eponai.server.datomic.pull :as p]
            [eponai.server.http :as h]))

(defn- user-not-found [email]
  (ex-info "Could not find user in db."
           {:cause   ::authentication-error
            :status  ::h/unathorized
            :data    {:email email}
            :message "Wrong email or password."}))

(defn- user-creds
  "Get user credentials for the specified email in the db. Returns nil if user does not exist.

  Throws ExceptionInfo if the user has not verified their email."
  [conn email]
  (let [db (db conn)]
    (when-let [db-user (p/user db email)]
      (let [password (p/password db db-user)
            verifications (p/verifications db db-user :user/email :verification.status/verified)]
        {:identity      (:db/id db-user)
         :username      (:user/email db-user)
         :password      (:password/bcrypt password)
         :roles         #{::user}
         :verifications verifications}))))

(defn add-bcrypt
  "Assoc :bcrypt key with hashed value of k in m."
  [m k]
  (assoc m :bcrypt (creds/hash-bcrypt (k m))))

(defmulti auth-map
          (fn [_ input] (::friend/workflow (meta input))))

(defmethod auth-map :form
  [conn input]
  (when-let [auth-map (creds/bcrypt-credential-fn
                        #(user-creds conn %)
                        input)]
    (if (seq (:verifications auth-map))
      (dissoc auth-map :verifications)
      (throw (ex-info "Email verification pending."
                      {:cause   ::verification-error
                       :status  ::h/unathorized
                       :data    {:email (:user/email (input :username))}
                       :message "Email verification pending"})))))

(defmethod auth-map :facebook
  [_ {:keys [access-token data]}]
  {:identity (:access_token access-token)
   :username (:user_id data)
   :roles    #{::user}})

(defn credential-fn
  "Create a credential fn with a db to pull user credentials.

  Returned function will dispatc on the ::friend/workflow and return the
  appropriate authentication map for the workflow."
  [conn]
  (fn [input]
    (auth-map conn input)))
