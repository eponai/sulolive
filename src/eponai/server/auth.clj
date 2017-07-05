(ns eponai.server.auth
  (:require
    [buddy.auth]
    [buddy.auth.accessrules :as buddy]
    [buddy.auth.backends :as backends]
    [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
    [buddy.auth.protocols :as auth.protocols]
    [manifold.deferred :as deferred]
    [environ.core :refer [env]]
    [taoensso.timbre :refer [error debug warn info]]
    [ring.util.response :as r]
    [eponai.server.datomic.format :as f]
    [eponai.server.external.auth0 :as auth0]
    [eponai.common.database :as db]
    [eponai.common.routes :as routes]
    [eponai.common.auth :as auth]
    [eponai.common.location :as location]
    [eponai.common :as c]
    [clojure.spec.alpha :as s]
    [medley.core :as medley]
    [clojure.string :as string]
    [eponai.common.mixpanel :as mixpanel]
    [taoensso.timbre :as timbre]
    [eponai.server.log :as log]
    [cemerick.url :as url]
    [eponai.client.routes :as client.routes]
    [eponai.common.format :as cf])
  (:import (clojure.lang ExceptionInfo)))

(def auth-token-cookie-name "sulo.token")
(def auth-token-remove-value "kill")

(def restrict buddy/restrict)

(defn prompt-login [request]
  (auth/-prompt-login (::auth-responder request) nil))

(defn unauthorize [request]
  (auth/-unauthorize (::auth-responder request)))

(defn redirect [request path]
  (auth/-redirect (::auth-responder request) path))

(defn remove-auth-cookie [response]
  (r/set-cookie response auth-token-cookie-name {:token auth-token-remove-value} {:max-age 1}))

(defn prompt-location-picker [request]
  (location/-prompt-location-picker (::location-responder request)))


;; Move this to location.clj?

(defn cookie-locality [request]
  (let [json-str (url/url-decode (get-in request [:cookies location/locality-cookie-name :value]))]
    (when (not-empty json-str)
      (try
        (c/read-transit json-str)
        (catch Exception e
          (debug "Exception when parsing cookie-locality: " e)
          nil)))))

(defn requested-location [request]
  (let [conn (:eponai.server.middleware/conn request)
        loc (cookie-locality request)
        {:keys [locality]} (:route-params request)]
    (if (and (some? locality)
             (not= (:sulo-locality/path loc) locality))
      (db/pull (db/db conn) [:db/id :sulo-locality/path] [:sulo-locality/path locality])
      loc)))

(defrecord HttpAuthResponder []
  auth/IAuthResponder
  (-redirect [this path]
    (r/redirect path))
  (-prompt-login [this _]
    (auth/-redirect this (routes/path :login)))
  (-unauthorize [this]
    (auth/-redirect this (routes/path :unauthorized))))

(defrecord HttpLocationResponder []
  location/ILocationResponder
  (-prompt-location-picker [this]
    ;; Routing to :landing-page instead of :landing-page/location
    ;; because this will be an entire page reload, so it'll take
    ;; a while before the screen scrolls down to "Vancouver / BC, Enter".
    (r/redirect (routes/path :landing-page))))

;(defn authenticate-auth0-user [conn auth0-user]
;  (when auth0-user
;    (let [db-user (db/lookup-entity (db/db conn) [:user/email (:email auth0-user)])]
;      (when-not db-user
;        (let [new-user (f/auth0->user auth0-user)]
;          (db/transact-one conn new-user))))))

(defn- token-map-from-cookie [request]
  (when-let [token-cookie (get-in request [:cookies auth-token-cookie-name :value])]
    (into {}
          (for [^String cookie-val (.split token-cookie "&")]
            (let [[k v] (map (fn [^String x] (.trim x))
                             (.split cookie-val "=" 2))]
              [(keyword k) v]))))
  )

(defn jwt-cookie-backend [conn auth0]
  (let [jws-backend (backends/jws {:secret   (auth0/secret auth0)
                                   ;; Throw it so we can handle it in our wrapper
                                   :on-error (fn [r e]
                                               (throw e))})]
    (reify
      auth.protocols/IAuthentication
      (-parse [_ request]
        (let [{:keys [token]} (token-map-from-cookie request)]
          token))
      (-authenticate [_ request data]
        ;; Parses the jws token and returns token validation errors
        (try
          (let [auth0-user (auth.protocols/-authenticate jws-backend request data)]
            (debug "Got auth0-user: " auth0-user)
            (when (some? auth0-user)
              (if-not (some? (:email auth0-user))
                (warn "Authenticated an auth0 user but it had no email. Auth0-user: " auth0-user)
                auth0-user
                ;(do (authenticate-auth0-user conn auth0-user)
                ;    auth0-user)
                )))
          (catch Exception e
            (when (not= data auth-token-remove-value)
              (debug "Authenticate exception. Unable to authenticate data: " data
                     " exception: ")
              (error e))
            (if-let [token-failure (when-let [data (ex-data e)]
                                     (let [{:keys [type cause]} data]
                                       (when (= type :validation)
                                         (condp = cause
                                           :exp ::token-expired
                                           :signature ::token-manipulated
                                           (do (warn "unknown validation type: " type)
                                               nil)))))]
              token-failure
              (do
                ;; TODO: Return nil to support multiple auth backends?
                (error "Error authenticating jws token: " e)
                (throw e))))))
      auth.protocols/IAuthorization
      (-handle-unauthorized [_ request metadata]
        (auth.protocols/-handle-unauthorized jws-backend request metadata)))))

(defn- wrap-http-responders [handler]
  (fn [request]
    (handler (assoc request ::auth-responder (->HttpAuthResponder)
                            ::location-responder (->HttpLocationResponder)))))

(defn- wrap-expired-token [handler]
  (let [token-failure #{::token-manipulated ::token-expired}]
    (fn [request]
      (if-not (contains? token-failure (:identity request))
        (handler request)
        (do
          (debug "Auth token failure: " (:identity request)
                 ", executing the request without auth. Will remove token from cookie.")
          (deferred/let-flow [response (handler (dissoc request :identity))]
                             ;; Force removal of the token, because it is failing.
                             (remove-auth-cookie response)))))))

(defn wrap-refresh-token [handler]
  (fn [request]
    (let [auth0 (get-in request [:eponai.server.middleware/system :system/auth0])]
      (if-not (auth0/should-refresh-token? auth0 (:identity request))
        (handler request)
        (let [{:keys [token tried-refresh-at]} (token-map-from-cookie request)
              refreshed-token (when (nil? tried-refresh-at)
                                (auth0/refresh auth0 token))]
          (debug "refresh token: " refreshed-token)
          (deferred/let-flow [response (handler request)]
                             (cond-> response
                                     ;; On token refresh, if no-one has set the cookies field, reset it.
                                     (empty? (get-in response [:cookies auth-token-cookie-name]))
                                     (r/set-cookie auth-token-cookie-name
                                                   (if (some? refreshed-token)
                                                     {:token refreshed-token}
                                                     {:token            token
                                                      :tried-refresh-at (or tried-refresh-at
                                                                            (System/currentTimeMillis))})
                                                   {:path "/"}))))))))

(defn wrap-on-auth [handler middleware]
  (let [wrapped (middleware handler)]
    (fn [request]
      (if (:identity request)
        (wrapped request)
        (handler request)))))

(defn wrap-auth [handler conn auth0]
  (let [auth-backend (jwt-cookie-backend conn auth0)]
    (-> handler
        (wrap-on-auth wrap-expired-token)
        (wrap-on-auth wrap-refresh-token)
        (wrap-authentication auth-backend)
        (wrap-authorization auth-backend)
        (wrap-http-responders))))


(defn existing-user [db auth0-profile]
  (when-let [email (:email auth0-profile)]
    (db/lookup-entity db [:user/email email])))

(defn- do-authenticate
  "Returns a logged in response if authenticate went well."
  [{:eponai.server.middleware/keys [system logger conn] :as request}
   token sulo-user]
  (let [loc (requested-location request)
        redirect-url (if (:sulo-locality/path loc)
                       (routes/path :index {:locality (:sulo-locality/path loc)})
                       (routes/path :landing-page))]
    (mixpanel/track "Sign in user" {:distinct_id (:db/id sulo-user)
                                    :ip          (:remote-addr request)})
    (r/set-cookie (r/redirect redirect-url) auth-token-cookie-name {:token token} {:path "/"})))

(defn authenticate
  [{:keys                          [params] :as request
    :eponai.server.middleware/keys [system logger conn]}]
  (debug "AUTHENTICATE: " params)
  (let [auth0 (:system/auth0 system)
        auth0management (:system/auth0management system)
        {:keys [code state token]} params

        ;; A code in the request means we want to login
        auth-map (when (some? code) (auth0/authenticate auth0 code state))

        ;; If we have a token in the request, it means this was the first login.
        ;; An account was created and we want to authenticate again
        token-info (when (some? token) {:profile (auth0/token-info auth0 token)
                                        :token   token})

        ;; access-token is only present in case of login first step
        {:keys    [profile access-token]
         id-token :token} (or auth-map token-info)

        ;; Get our user from Datomic
        sulo-user (existing-user (db/db conn) profile)
        user-id (:sub profile)]

    ;; If we already have a user for this account and they're verified, we can authenticate
    (if (some? sulo-user)
      (let [should-verify? (and (not (:user/verified sulo-user)) (not-empty (:email profile)))]
        (when should-verify?
          (db/transact conn [[:db/add (:db/id sulo-user) :user/verified true]]))
        (try
          (auth0/link-with-same-email auth0management (assoc profile :user_id user-id))
          (catch ExceptionInfo e
            (error e)))
        (do-authenticate request id-token sulo-user))

      ;; User is signin in for the first time, and should go through creating an account.
      (let [path (routes/path :login nil (cf/remove-nil-keys
                                           {:access_token access-token
                                            :token        id-token}))]
        (debug "Redirect to path: " path)
        (r/redirect path)))))

(defn link-user [{:keys                          [params] :as request
                  :eponai.server.middleware/keys [system logger conn]}]
  (let [auth0 (:system/auth0 system)
        auth0management (:system/auth0management system)
        primary-token (token-map-from-cookie request)
        primary-profile (auth0/token-info auth0 (:token primary-token))

        {:keys [code state token]} params
        {:keys [profile]} (auth0/authenticate auth0 code state)

        primary-user (auth0/get-user auth0management primary-profile)]

    (try
      (if (auth0/email-provider? profile)
        (throw (ex-info "Already connected to another account"
                        {:message "Something went wrong and we couldnt' connect your social account. Please try again later."}))
        (do
          (auth0/link-user-accounts-by-id auth0management (auth0/user-id primary-user) (auth0/user-id profile))
          (r/redirect (routes/path :user-settings))))
      (catch ExceptionInfo e
        (error e)
        (r/redirect (routes/path :user-settings nil {:error "connect"}))))))


(defn agent-whitelisted? [request]
  (let [whitelist #{"facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
                    "facebookexternalhit/1.1"
                    "Facebot"
                    "Twitterbot"}
        user-agent (get-in request [:headers "user-agent"])]
    (when (some? user-agent)
      (some #(string/includes? user-agent %) whitelist))))

(defn bidi-route-restrictions
  "For each bidi route, we get the roles required for the route
  and check if the auth in the request is allowed to access this route.

  When the request fails to authenticate, we either prompt the requestee
  to login or respond with unauthorized."
  [route]
  (let [auth-roles (routes/auth-roles route)]
    {:handler  (fn [{:keys [identity route-params headers] :as request}]
                 (let [db (db/db (:eponai.server.middleware/conn request))
                       route-params (client.routes/normalize-route-params route-params db)
                       auth-val {:route        route
                                 :route-params route-params
                                 :auth-roles   auth-roles
                                 :auth         identity}]
                   (if (or (agent-whitelisted? request)
                           (auth/authed-for-roles?
                             db
                             auth-roles
                             identity
                             route-params))
                     (buddy/success auth-val)
                     (buddy/error auth-val))))
     :on-error (fn [request v]
                 (debug "Unable to authorize user: " v)
                 (cond
                   ;; For now we send users to :coming-soon
                   ;; TODO: Prompt login then redirect back to where the user came from.
                   (nil? (:auth v))
                   (prompt-login request)
                   ;(redirect request (routes/path :landing-page))
                   :else
                   (unauthorize request)))}))


(defn bidi-location-redirect
  [route]
  {:handler  (fn [{:keys [identity route-params headers] :as request}]
               (if (= route :landing-page)
                 (if-let [loc (cookie-locality request)]
                   (if (auth/authed-for-roles?
                         (db/db (:eponai.server.middleware/conn request))
                         ::auth/any-user
                         identity
                         nil)
                     (buddy/error loc)
                     (buddy/success))
                   (buddy/success))
                 (buddy/success)))
   :on-error (fn [{:keys [eponai.server.middleware/conn]} loc]
               (debug "Redirect: " loc)
               (r/redirect (routes/path :index {:locality (:sulo-locality/path loc)})))})
