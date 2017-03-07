(ns eponai.server.external.auth0
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [buddy.core.codecs.base64 :as b64]
    [buddy.sign.jws :as jws]
    [eponai.common.database :as db]
    [eponai.server.external.host :as server-address]
    [eponai.common.routes :as routes]
    [taoensso.timbre :refer [debug]]))

(defprotocol IAuth0
  (secret [this] "Returns the jwt secret for unsigning tokens")
  (authenticate [this code state] "Returns an authentcation map"))

(defn- read-json [json]
  (json/read-json json true))

(defn auth0 [client-id client-secret server-address]
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
    (reify IAuth0
      (secret [this] (b64/decode client-secret))
      (authenticate [this code state]
        (if (some? code)
          (let [{:keys [id_token access_token token_type] :as to} (code->token code)
                profile (token->profile access_token)]
            (debug "Got Token auth0: " to)
            {:token        id_token
             :profile      profile
             :redirect-url state
             :token-type   token_type})
          {:redirect-url state})))))

(defn auth0-stub
  "Gets authenticated as the email passed as code parameter"
  [conn]
  (reify IAuth0
    (secret [this] (.getBytes "sulo-dev-secret"))
    (authenticate [this code state]
      ;; Fill this in with something we can use to auth with.
      ;; Example token:
      ;; {:access_token "MszrmhW0sXqYs0LW", :expires_in 86400, :id_token "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJlbWFpbCI6ImRldkBzdWxvLmxpdmUiLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiaXNzIjoiaHR0cHM6Ly9zdWxvLmF1dGgwLmNvbS8iLCJzdWIiOiJhdXRoMHw1ODgzZGFmYmRlMzVmNTBlOWMxNzlhNDMiLCJhdWQiOiJKTXFDQm5nSGdPY1NZQndsVkNHMmh0ckt4UUZsZHpEaCIsImV4cCI6MTQ4ODc4NjE4MywiaWF0IjoxNDg4NzUwMTgzfQ.MON-uQh4DqDkZQ0CvF_UlufELZ9Li-f3RNXYAYsqvn0", :token_type "Bearer"}
      ;; ->
      ;; {:email "dev@sulo.live", :email_verified true, :iss "https://sulo.auth0.com/", :sub "auth0|5883dafbde35f50e9c179a43", :aud "JMqCBngHgOcSYBwlVCG2htrKxQFldzDh", :exp 1488786183, :iat 1488750183}
      (if-let [email (:user/email (db/lookup-entity (db/db conn) [:user/email code]))
               now (/ (System/currentTimeMillis) 1000)
               tomorrow (+ now (* 24 3600))
               auth-data {:email          email
                          :email_verified true
                          :iss            "localhost"
                          :iat            now
                          :exp            tomorrow}
               jwt-secret (secret this)]
        (do (debug "Authing self-signed jwt token on localhost with auth-data: " auth-data)
            {:token        (jws/sign auth-data jwt-secret)
             :token-type   "Bearer"
             :redirect-url state})
        {:redirect-url state}))))
