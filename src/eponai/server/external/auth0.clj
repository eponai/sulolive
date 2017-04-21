(ns eponai.server.external.auth0
  (:require
    [clj-http.client :as http]
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
  (authenticate [this code state] "Returns an authentcation map")
  (refresh [this token] "Takes a token and returns a new one with a later :exp value"))

(defn- read-json [json]
  (json/read-json json true))

(defrecord Auth0 [client-id client-secret server-address]
  IAuth0
  (secret [this] (b64/decode client-secret))
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
              profile nil]
          ;; TODO: Do we ever need the profile of the token?
          ;; We're not using it right now, so let's avoid that http request.
          ;; profile (token->profile access_token)
          (debug "Got Token auth0: " to)
          {:token        id_token
           :profile      profile
           :redirect-url state
           :token-type   token_type})
        {:redirect-url state})))
  (refresh [this token]
    (letfn [(token->refreshed-token [token]
              (-> (http/post "https://sulo.auth0.com/delegation"
                             {:form-params {:client_id     client-id
                                            :client_secret client-secret
                                            :grant_type    "urn:ietf:params:oauth:grant-type:jwt-bearer"
                                            :id_token      token}})
                  (:body)
                  (read-json)
                  (:id_token))
              (read-json (:body)))]
      (try
        (token->refreshed-token token)
        (catch Exception e
          (error "Error refreshing token: " e ", returning old one.")
          token)))))

(defrecord FakeAuth0 [datomic]
  IAuth0
  (secret [this]
    (.getBytes "sulo-dev-secret"))
  (authenticate [this code state]
    (if-let [email (:user/email (db/lookup-entity (db/db (:conn (:datomic this))) [:user/email code]))]
      (let [now (long (/ (System/currentTimeMillis) 1000))
            tomorrow (+ now (* 24 3600))
            auth-data {:email          email
                       :email_verified true
                       :iss            "localhost"
                       :iat            now
                       :exp            tomorrow}
            jwt-secret (secret this)]
        (debug "Authing self-signed jwt token on localhost with auth-data: " auth-data)
        {:token        (jwt/sign auth-data jwt-secret)
         :token-type   "Bearer"
         :redirect-url state})
      {:redirect-url state}))
  (refresh [this token]
    (let [{:keys [email]} (jws/unsign token (secret this))]
      (:token (authenticate this email "")))))
