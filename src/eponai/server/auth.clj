(ns eponai.server.auth
  (:require
    [buddy.auth.accessrules :as buddy]
    [buddy.auth.backends :as backends]
    [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
    [buddy.core.codecs.base64 :as b64]
    [buddy.auth.protocols :as auth.protocols]
    [environ.core :refer [env]]
    [taoensso.timbre :refer [error debug]]
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [ring.util.response :as r]
    [eponai.server.datomic.format :as f]
    [eponai.server.external.aws-elb :as aws-elb]
    [eponai.common.database :as db]))

(def restrict buddy/restrict)

(defn is-logged-in? [identity]
  (= (:iss identity) "sulo.auth0.com"))

(defn- auth0-code->token [code aws-elb]
  (json/read-json
    (:body (http/post "https://sulo.auth0.com/oauth/token"
                      {:form-params {:client_id     (env :auth0-client-id)
                                     :redirect_uri  (if (aws-elb/is-staging? aws-elb)
                                                      (str (aws-elb/env-url aws-elb) "/auth")
                                                      (str (env :server-url-schema) "://" (env :server-url-host) "/auth"))
                                     :client_secret (env :auth0-client-secret)
                                     :code          code
                                     :grant_type    "authorization_code"}}))
    true))

(defn- auth0-token->profile [token]
  (json/read-json
    (:body (http/get "https://sulo.auth0.com/userinfo/?"
                     {:query-params {"access_token" token}}))
    true))

(defn auth0 [{:keys [params] :as req}]
  (debug "AUTHING REQUEST: " req)
  (let [{:keys [code state]} params
        aws-elb (get-in req [:eponai.server.middleware/system :system/aws-elb])]
    (if (some? code)
      (let [{:keys [id_token access_token token_type] :as to} (auth0-code->token code aws-elb)
            profile (auth0-token->profile access_token)]
        (debug "Got Token auth0: " to)
        {:token        id_token
         :profile      profile
         :redirect-url state
         :token-type token_type})
      {:redirect-url state})))

(defn authenticated? [request]
  (debug "Authing request id: " (:identity request))
  (boolean (:identity request)))

(defn member-restrict-opts []
  {:handler  authenticated?
   :on-error (fn [a b]
               (debug "A: " a " B: " b)
               (r/redirect "/coming-soon")
               ;{:status  401
               ; :headers {"Content-Type"     "text/plain"
               ;           "WWW-Authenticate" (format "Basic realm=\"%s\"" http-realm)}}
               )})

(defn jwt-restrict-opts []
  {:handler (fn [req] (debug "Identity: " (:identity req)) true)
   :on-error (fn [& _]
               (debug "Unauthorized api request")
               {:status 401
                :headers {}
                :body "You fucked up"})})

(defn authenticate-auth0-user [conn auth0-user]
  (when auth0-user
    (let [db-user (db/lookup-entity (db/db conn) [:user/email (:email auth0-user)])]
      (when-not db-user
        (let [new-user (f/auth0->user auth0-user)]
          (db/transact-one conn new-user))))))

(defn jwt-cookie-backend [conn cookie-key]
  (let [jws-backend (backends/jws {:secret   (b64/decode (env :auth0-client-secret))
                                   :on-error (fn [r e]
                                               (error e))})]
    ;; Only changes how the token is parsed. Parses from cookie instead of header
    (reify
      auth.protocols/IAuthentication
      (-parse [_ request]
        (get-in request [:cookies cookie-key :value]))
      (-authenticate [_ request data]
        (let [auth0-user (auth.protocols/-authenticate jws-backend request data)]
          (when (some? auth0-user)
            (authenticate-auth0-user conn auth0-user)
            auth0-user)))
      auth.protocols/IAuthorization
      (-handle-unauthorized [_ request metadata]
        (auth.protocols/-handle-unauthorized jws-backend request metadata)))))


(def auth-token-cookie-name "sulo-auth-token")

(defn wrap-auth [handler conn]
  (let [auth-backend (jwt-cookie-backend conn auth-token-cookie-name)]
    (-> handler
        (wrap-authorization auth-backend)
        (wrap-authentication auth-backend))))

(defn authenticate [request]
  (let [{:keys [redirect-url token]} (auth0 request)]
    (if token
      (r/set-cookie (r/redirect redirect-url) auth-token-cookie-name token)
      (r/redirect "/coming-soon"))))

(defn logout [request]
  (-> (r/redirect "/coming-soon")
      (assoc-in [:cookies auth-token-cookie-name] {:value "kill" :max-age 1})))
