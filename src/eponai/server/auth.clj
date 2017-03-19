(ns eponai.server.auth
  (:require
    [buddy.auth.accessrules :as buddy]
    [buddy.auth.backends :as backends]
    [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
    [buddy.auth.protocols :as auth.protocols]
    [environ.core :refer [env]]
    [taoensso.timbre :refer [error debug]]
    [ring.util.response :as r]
    [eponai.server.datomic.format :as f]
    [eponai.server.external.auth0 :as auth0]
    [eponai.common.database :as db]))

(def restrict buddy/restrict)

(defn is-logged-in? [identity]
  (= (:iss identity) "sulo.auth0.com"))

(defn authenticated? [request]
  (debug "Authing request id: " (:identity request))
  (boolean (:identity request)))

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

(defn authenticate-auth0-user [conn auth0-user]
  (when auth0-user
    (let [db-user (db/lookup-entity (db/db conn) [:user/email (:email auth0-user)])]
      (when-not db-user
        (let [new-user (f/auth0->user auth0-user)]
          (db/transact-one conn new-user))))))

(defn jwt-cookie-backend [conn auth0 cookie-key]
  (let [jws-backend (backends/jws {:secret   (auth0/secret auth0)
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

(defn wrap-auth [handler conn auth0]
  (let [auth-backend (jwt-cookie-backend conn auth0 auth-token-cookie-name)]
    (-> handler
        (wrap-authorization auth-backend)
        (wrap-authentication auth-backend))))

(defn authenticate [{:keys [params] :as request}]
  (let [auth0 (get-in request [:eponai.server.middleware/system :system/auth0])
        {:keys [code state]} params
        {:keys [redirect-url token]} (auth0/authenticate auth0 code state)]
    (if token
      (r/set-cookie (r/redirect redirect-url) auth-token-cookie-name token)
      (r/redirect "/coming-soon"))))

(defn logout [request]
  (-> (r/redirect "/coming-soon")
      (assoc-in [:cookies auth-token-cookie-name] {:value "kill" :max-age 1})))
