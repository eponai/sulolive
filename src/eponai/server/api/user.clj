(ns eponai.server.api.user
  (:require
    [eponai.common.database :as db]
    [taoensso.timbre :refer [debug]]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.datomic.format :as f]
    [taoensso.timbre :refer [info debug]]))

(defn ->order [state o store-id user-id]
  (let [store (db/lookup-entity (db/db state) store-id)]
    (-> o
        (assoc :order/store store :order/user user-id))))

(defn create [{:keys [state]} {:keys [user]}]
  (if-let [old-user (db/lookup-entity (db/db state) [:user/email (:user/email user)])]
    (throw (ex-info "User with email alreay exists"
                    {:message "You already have an account with that email"
                     :error   :error/account-exists
                     :user-id (:db/id old-user)}))
    (let [new-user (f/user user)]
      (info "User - authenticated user did not exist, creating new user: " new-user)
      (db/transact-one state new-user)
      {:user new-user})))

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