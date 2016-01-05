(ns eponai.server.auth.credentials
  (:require [cemerick.friend :as friend]
            [cemerick.friend.credentials :as creds]
            [datomic.api :refer [db]]
            [eponai.server.datomic.pull :as p]
            [eponai.server.http :as h]
            [eponai.server.datomic.transact :as t]
            [eponai.server.auth.facebook :as fb]))

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

(defmethod auth-map :default
  [conn input]
  (when-let [auth-map (creds/bcrypt-credential-fn
                        #(user-creds conn %)
                        input)]
    (if (seq (:verifications auth-map))
      (dissoc auth-map :verifications)
      (throw (ex-info "Email verification pending."
                      {:cause   ::authentication-error
                       :status  ::h/unathorized
                       :data    {:email (:user/email (input :username))}
                       :message "Email verification pending"})))))

(defmethod auth-map :facebook
  [conn {:keys [access_token user_id]}]
  (let [auth   {:identity access_token
                :username user_id
                :roles    #{::user}}
        db (db conn)
        db-fb-user (p/fb-user db  (Long/parseLong user_id))]
    ; If we already have a facebook user, no need to add a new one to the database.
    (if db-fb-user
      auth
      (let [{:keys [email] :as resp} (fb/user-info user_id access_token)
            db-user (p/user db email)]
        (println "User-info response: " resp)
        ; If we already have a user with the email for the fb account, we don't add a new user but throw exception.
        (if-not db-user
          (do
            (t/new-fb-user conn (Long/parseLong user_id) access_token email)
            auth)
          (throw (ex-info "User with email already exists."
                          {:cause   ::authentication-error
                           :status  ::h/unathorized
                           :data    {:email email}
                           :message "Email connected to the Facebook account is already in use."})))))))

(defn credential-fn
  "Create a credential fn with a db to pull user credentials.

  Returned function will dispatc on the ::friend/workflow and return the
  appropriate authentication map for the workflow."
  [conn]
  (fn [input]
    (auth-map conn input)))
