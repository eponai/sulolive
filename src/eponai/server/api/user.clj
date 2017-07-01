(ns eponai.server.api.user
  (:require
    [eponai.common.database :as db]
    [taoensso.timbre :refer [debug]]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.datomic.format :as f]
    [taoensso.timbre :refer [info debug]]
    [eponai.server.external.auth0 :as auth0]
    [eponai.common.format :as cf]
    [clojure.string :as string]))

(defn ->order [state o store-id user-id]
  (let [store (db/lookup-entity (db/db state) store-id)]
    (-> o
        (assoc :order/store store :order/user user-id))))

(defn user-info [{:keys [system auth query-params]}]
  (let [profile (when (:token query-params) (auth0/token-info (:system/auth0 system) (:token query-params)))
        _ (debug "Will get user for profile: " profile)
        user (auth0/get-user (:system/auth0management system) (or profile auth))
        _ (debug "Got user: " user)
        _ (debug "Found user for auth: " auth)
        identity* (fn [i]
                    (debug "Getting identity: " i)
                    (let [{:keys [profileData provider connection user_id]} i]
                      (cf/remove-nil-keys
                        {:auth0.identity/email       (:email profileData)
                         :auth0.identity/id          user_id
                         :auth0.identity/connection  connection
                         :auth0.identity/provider    provider
                         :auth0.identity/name        (:name profileData)
                         :auth0.identity/picture     (:picture profileData)
                         :auth0.identity/screen-name (:screen_name profileData)})))]
    {:auth0/identities (map identity* (:identities user))
     :auth0/nickname   (or (:screen_name user) (:nickname user) (:name user))
     :auth0/email      (:email user)}))

(defn unlink-user [{:keys [system auth]} params]
  (debug "Will unlink account " params)
  (auth0/unlink-user-accounts (:system/auth0management system)
                              auth
                              (:user-id params)
                              (:provider params)))

(defn create [{:keys [state system query-params]} {:keys [user id-token]}]
  (debug "QUERY PARAMS: " id-token)
  (let [auth0-user (auth0/token-info (:system/auth0 system) id-token)]
    (debug "Got secondary user: " auth0-user)
    (if-let [old-user (db/lookup-entity (db/db state) [:user/email (:user/email user)])]
      (throw (ex-info "User with email alreay exists"
                      {:message "You already have an account with that email"
                       :error   :error/account-exists
                       :user-id (:db/id old-user)}))
      (let [new-user (f/user user)
            ;provider (first (string/split (:user_id auth0-user) #"\|"))
            auth0manage (:system/auth0management system)]
        (info "User - authenticated user did not exist, creating new user: " new-user)
        (db/transact-one state new-user)

        (let [new-auth0-user (auth0/create-email-user auth0manage (:user/email new-user))]
          (auth0/link-user-accounts-by-id auth0manage
                                          (auth0/user-id new-auth0-user)
                                          (auth0/user-id auth0-user)))

        (let [db-user (db/pull (db/db state) [:user/verified :db/id] [:user/email (:user/email new-user)])]
          (debug "Created user: " db-user)
          {:user db-user})))))

(defn customer [{:keys [auth state system]}]
  (debug "Pull stripe " auth)
  (let [stripe-user (stripe/pull-user-stripe (db/db state) (:user-id auth))]
    (debug "Stripe user: " stripe-user)
    (when stripe-user
      (assoc (stripe/get-customer (:system/stripe system) (:stripe/id stripe-user)) :db/id (:db/id stripe-user)))))


;(let [old-user (db/lookup-entity (db/db conn) [:user/email email])
;      user (if-not (some? old-user)
;             (let [new-user (f/auth0->user profile)
;                   _ (info "Auth - authenticated user did not exist, creating new user: " new-user)
;                   result (db/transact-one conn new-user)]
;               (debug "Auth - new user: " new-user)
;               (db/lookup-entity (:db-after result) [:user/email email]))
;             old-user)]
;  (when-not (some? old-user)
;    (log/info! logger ::user-created {:user-id (:db/id user)}))
;  (when token
;    (let [loc (requested-location request)
;          redirect-url (if (:sulo-locality/path loc)
;                         (routes/path :index {:locality (:sulo-locality/path loc)})
;                         (routes/path :landing-page))]
;      (debug "Redirect to URL: " redirect-url)
;      (mixpanel/track "Sign in user" {:distinct_id (:db/id user)
;                                      :ip          (:remote-addr request)})
;      (r/set-cookie (r/redirect redirect-url) auth-token-cookie-name {:token token} {:path "/"})))))