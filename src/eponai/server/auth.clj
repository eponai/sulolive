(ns eponai.server.auth
  (:require
    [buddy.auth.accessrules :as buddy]
    [buddy.auth.backends :as backends]
    [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
    [buddy.core.codecs.base64 :as b64]
    [buddy.auth.protocols :as auth.protocols]
    [environ.core :refer [env]]
    [eponai.server.ui.common :as common]
    [taoensso.timbre :refer [error debug]]
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [ring.util.response :as r]))

(def http-realm "Sulo-Prototype")

(def restrict buddy/restrict)

(defn is-logged-in? [identity]
  (= (:iss identity) "sulo.auth0.com"))

(defn- auth0-code->token [code]
  (json/read-json
    (:body (http/post "https://sulo.auth0.com/oauth/token"
                      {:form-params {:client_id     (env :auth0-client-id)
                                     :redirect_uri  (str (env :server-url-schema) "://" (env :server-url-host) "/auth")
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
  (let [{:keys [code state]} params]
    (if (some? code)
      (let [{:keys [id_token access_token token_type] :as to}  (auth0-code->token code)
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

(defn http-basic-restrict-opts []
  {:handler  authenticated?
   :on-error (fn [a b]
               {:status  401
                :headers {"Content-Type"     "text/plain"
                          "WWW-Authenticate" (format "Basic realm=\"%s\"" http-realm)}})})

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

(defn- jwt-backend []
  (backends/jws {:secret     (b64/decode (env :auth0-client-secret))
                 :token-name "Bearer"
                 :on-error   (fn [r e]
                               (error e))}))

(defn jwt-cookie-backend [cookie-key]
  (let [jws-backend (backends/jws {:secret   (b64/decode (env :auth0-client-secret))
                                   :on-error (fn [r e]
                                               (error e))})]
    ;; Only changes how the token is parsed. Parses from cookie instead of header
    (reify
      auth.protocols/IAuthentication
      (-parse [_ request]
        (get-in request [:cookies cookie-key :value]))
      (-authenticate [_ request data]
        (auth.protocols/-authenticate jws-backend request data))
      auth.protocols/IAuthorization
      (-handle-unauthorized [_ request metadata]
        (auth.protocols/-handle-unauthorized jws-backend request metadata)))))

(defn- http-backend []
  (let [auth-fn (fn [req {:keys [username password] :as token}]
                  (when (and (= password "hejsan") (= username "sulo"))
                    {:user "admin"}))]

    (backends/http-basic {:authfn auth-fn
                          :realm  http-realm})))

(defn wrap-auth [handler conn]
  (let [auth-backend (jwt-cookie-backend "token")
        basic-backend (http-backend)]
    (-> handler
        (wrap-authorization auth-backend)
        (wrap-authentication auth-backend basic-backend))))

(defn inline-auth-code [{:keys [token profile token-type redirect-url]}]
  (let [new-location (if redirect-url (str "'" redirect-url "'") "window.location.origin")]
    (cond-> []
            (some? token)
            (conj (str "localStorage.setItem('idToken', '" token "');"))
            ;(some? profile)
            ;(conj "localStorage.setItem('profile', '" (json/write-str profile) "');")
            (some? token-type)
            (conj (str "localStorage.setItem('tokenType', '" token-type "');"))
            :always
            (conj (str "window.location = '" redirect-url "'"))
            )))

(defui Auth
  Object
  (render [this]
    (let [{:keys [token] :as props} (om/props this)]
      (debug "Auth Props: " props)
      (dom/html
        {:lang "en"}
        (dom/body
          nil
          (common/inline-javascript (inline-auth-code props)))))))
