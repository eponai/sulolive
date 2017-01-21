(ns eponai.server.auth
  (:require
    [buddy.auth.accessrules :as buddy]
    [buddy.auth.backends :as backends]
    [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
    [buddy.core.codecs.base64 :as b64]
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
                                     :redirect_uri  "http://localhost:3000/auth"
                                     :client_secret (env :auth0-client-secret)
                                     :code          code
                                     :grant_type    "authorization_code"}}))
    true))

(defn- auth0-token->profile [token]
  (json/read-json
    (:body (http/get "https://sulo.auth0.com/userinfo/?"
                     {:query-params {"access_token" token}}))
    true))

(defn auth0 [{:keys [params]}]
  (let [{:keys [code state]} params]
    (if (some? code)
      (let [{:keys [id_token access_token token_type]}  (auth0-code->token code)
            profile (auth0-token->profile access_token)]
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

(defn- http-backend []
  (let [auth-fn (fn [req {:keys [username password] :as token}]
                  (when (and (= password "hejsan") (= username "sulo"))
                    {:user "admin"}))]

    (backends/http-basic {:authfn auth-fn
                          :realm  http-realm})))

(defn wrap-auth [handler conn]
  (let [auth-backend (jwt-backend)
        basic-backend (http-backend)]
    (-> handler
        (wrap-authorization auth-backend)
        (wrap-authentication auth-backend basic-backend))))

(defn inline-auth-code [{:keys [token profile token-type redirect-url]}]
  (cond-> []
          (some? token)
          (conj "localStorage.setItem('idToken', '" token "');")
          ;(some? profile)
          ;(conj "localStorage.setItem('profile', '" (json/write-str profile) "');")
          (some? token-type)
          (conj "localStorage.setItem('tokenType', '" token-type "');")
          :always
          (conj "window.location = '" redirect-url "'")))

(defui Auth
  Object
  (render [this]
    (let [props (om/props this)]
      (dom/html
        {:lang "en"}
        (dom/body
          nil
          (common/inline-javascript (inline-auth-code props)))))))
