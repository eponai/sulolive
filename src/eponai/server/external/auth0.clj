(ns eponai.server.external.auth0
  (:require
    [clj-http.client :as http]
    [clj-time.core :as time]
    [clj-time.coerce :as time.coerce]
    [clojure.data.json :as json]
    [buddy.core.codecs.base64 :as b64]
    [buddy.sign.jwt :as jwt]
    [eponai.common.database :as db]
    [eponai.server.external.host :as server-address]
    [eponai.common.routes :as routes]
    [taoensso.timbre :refer [debug error]]
    [buddy.sign.jws :as jws]))

(defprotocol IAuth0
  (secret [this] "Returns the jwt secret for unsigning tokens")
  (token-info [this token] "Returns the jwt secret for unsigning tokens")
  (authenticate [this code state] "Returns an authentcation map")
  (refresh [this token] "Takes a token and returns a new one with a later :exp value. Return nil if it was not possible.")
  (should-refresh-token? [this parsed-token] "Return true if it's time to refresh a token"))

(defn- read-json [json]
  (json/read-json json true))

(defn token-expiring-within?
  "Returns true if the token expires in less than provided date.

  Takes an extra 'now' parameter for testing."
  ([token date]
   (token-expiring-within? token date (time/now)))
  ([{:keys [exp]} date now]
   (when exp
     (time/within? now
                   (time/plus now date)
                   (time.coerce/from-long (* 1000 exp))))))

(defrecord Auth0 [client-id client-secret server-address]
  IAuth0
  (secret [this] client-secret)
  (token-info [this token]
    (jwt/unsign token (secret this)))
  (authenticate [this code state]
    (letfn [(code->token [code]
              (read-json
                (:body (http/post "https://sulo.auth0.com/oauth/token"
                                  {:form-params {:client_id     client-id
                                                 :redirect_uri  (str (server-address/webserver-url server-address)
                                                                     (routes/path :auth))
                                                 :client_secret client-secret
                                                 :code          code
                                                 :grant_type    "authorization_code"}}))))
            (token->profile [token]
              (read-json
                (:body (http/get "https://sulo.auth0.com/userinfo/?"
                                 {:query-params {"access_token" token}}))))]

      (if (some? code)
        (let [{:keys [id_token access_token token_type] :as to} (code->token code)
              profile (token->profile access_token)]
          ;; TODO: Do we ever need the profile of the token?
          ;; We're not using it right now, so let's avoid that http request.
          ;; profile (token->profile access_token)
          (debug "Got Token auth0: " to)
          {:token        id_token
           :access_token access_token
           :profile      profile
           :redirect-url state
           :token-type   token_type})
        {:redirect-url state})))
  (refresh [this token]
    (letfn [(token->refreshed-token [token]
              (let [response (http/post "https://sulo.auth0.com/delegation"
                                        {:form-params {:client_id     client-id
                                                       :client_secret client-secret
                                                       :grant_type    "urn:ietf:params:oauth:grant-type:jwt-bearer"
                                                       :scope         "openid email"
                                                       :id_token      token}})]
                (debug "Response after requesting a refreshed token: " response)
                (-> response :body (read-json) :id_token)))]
      (try
        (token->refreshed-token token)
        (catch Exception e
          (error "Error refreshing token: " e)
          nil))))
  (should-refresh-token? [this parsed-token]
    (token-expiring-within? parsed-token (time/weeks 1))))

(defrecord FakeAuth0 [datomic]
  IAuth0
  (secret [this]
    (.getBytes "sulo-dev-secret"))
  (token-info [this token]
    (jwt/unsign token (secret this)))
  (authenticate [this code state]
    (let [email code
          now (quot (System/currentTimeMillis) 1000)
          tomorrow (+ now (* 24 3600))
          auth-data {:email          email
                     :email_verified true
                     :iss            "localhost"
                     :iat            now
                     :exp            tomorrow}
          jwt-secret (secret this)]
      (debug "Authing self-signed jwt token on localhost with auth-data: " auth-data)
      {:token        (jwt/sign auth-data jwt-secret)
       :profile      auth-data
       :token-type   "Bearer"
       :redirect-url state}))
  (refresh [this token]
    (letfn [(token->refreshed-token [token]
              (let [{:keys [email]} (token-info this token)]
                (:token (authenticate this email ""))))]
      (try
        (token->refreshed-token token)
        (catch Exception e
          (debug "Unable to parse token: " token " error: " e)
          nil))))
  (should-refresh-token? [this parsed-token]
    (token-expiring-within? parsed-token (time/minutes 59))))
