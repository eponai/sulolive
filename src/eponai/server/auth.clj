(ns eponai.server.auth
  (:require
    [buddy.auth]
    [buddy.auth.accessrules :as buddy]
    [buddy.auth.backends :as backends]
    [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
    [buddy.auth.protocols :as auth.protocols]
    [manifold.deferred :as deferred]
    [environ.core :refer [env]]
    [taoensso.timbre :refer [error debug]]
    [ring.util.response :as r]
    [eponai.server.datomic.format :as f]
    [eponai.server.external.auth0 :as auth0]
    [eponai.common.database :as db]
    [eponai.common.routes :as routes]
    [eponai.common :as c]))

(def login-route (routes/path :coming-soon))
(def auth-token-cookie-name "sulo-auth-token")

(defprotocol IAuthResponse
  (-reset-cookies-auth [this response])
  (-logout [this request redirect-route]))

(defrecord HttpLogoutResponse []
  IAuthResponse
  (-reset-cookies-auth [this response]
    (assoc-in response [:cookies auth-token-cookie-name] {:value "kill" :max-age 1}))
  (-logout [this request redirect-route]
    (->> (r/redirect (or redirect-route login-route))
         (-reset-cookies-auth this))))

(defn reset-cookies-auth [request response]
  (-reset-cookies-auth (::logout-responder request) response))

(defn logout [request]
  (-logout (::logout-responder request) request (::redirect-route request)))

(defn- wrap-expired-token [handler]
  (fn [request]
    (if (not= ::token-expired (:identity request))
      (handler request)
      (fn [request]
        (deferred/let-flow [response (handler request)]
                           (reset-cookies-auth request response))))))

(defn restrict [handler rule]
  (buddy/restrict (wrap-expired-token handler) rule))

(defn is-logged-in? [identity]
  (= (:iss identity) "sulo.auth0.com"))

(defn authenticated? [request]
  (debug "Authing request id: " (:identity request))
  (boolean (:identity request)))

(defn member-restrict-opts []
  {:handler  authenticated?
   :on-error (fn [a b]
               (r/redirect login-route)
               ;{:status  401
               ; :headers {"Content-Type"     "text/plain"
               ;           "WWW-Authenticate" (format "Basic realm=\"%s\"" http-realm)}}
               )})

(defn jwt-restrict-opts []
  {:handler  (fn [req] (debug "Identity: " (:identity req)) true)
   :on-error (fn [& _]
               (debug "Unauthorized api request")
               {:status  401
                :headers {}
                :body    "You fucked up"})})

(defn authenticate-auth0-user [conn auth0-user]
  (when auth0-user
    (let [db-user (db/lookup-entity (db/db conn) [:user/email (:email auth0-user)])]
      (when-not db-user
        (let [new-user (f/auth0->user auth0-user)]
          (db/transact-one conn new-user))))))

(defn- token-expired? [e]
  (when-let [data (ex-data e)]
    (let [{:keys [type cause]} data]
      (and (= type :validation)
           (= cause :exp)))))

(defn jwt-cookie-backend [conn auth0 cookie-key]
  (let [jws-backend (backends/jws {:secret   (auth0/secret auth0)
                                   :on-error (fn [r e]
                                               (if (token-expired? e)
                                                 ::token-expired
                                                 (do (debug "Error authenticating jws token: " e)
                                                     (throw e))))})]
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

(defn wrap-auth [handler conn auth0]
  (let [auth-backend (jwt-cookie-backend conn auth0 auth-token-cookie-name)
        wrap-logout-response (fn [handler]
                               (fn [request]
                                 (handler (assoc request ::logout-responder (->HttpLogoutResponse)))))]
    (-> handler
        (wrap-authentication auth-backend)
        (wrap-authorization auth-backend)
        (wrap-logout-response))))

(defn authenticate [{:keys [params] :as request}]
  (let [auth0 (get-in request [:eponai.server.middleware/system :system/auth0])
        {:keys [code state]} params
        {:keys [redirect-url token]} (auth0/authenticate auth0 code state)]
    (if token
      (r/set-cookie (r/redirect redirect-url) auth-token-cookie-name token)
      (r/redirect login-route))))

;; TODO: Might want to structure this differently.
;;       Because how do we query "which role does this user have for which parameters?"
;;       We'd want, User has ::exact-user role for :user-id [123]
;;                  User has ::store-owner role for :store-id [234 345]?
;;      This might not be useful.
(defn auth-query [roles {:keys [email]} route-params]
  (let [roles (cond-> roles
                      (keyword? roles) (hash-set)
                      (sequential? roles) (set))
        user-id (when (::routes/user roles)
                  (c/parse-long-safe
                    (or (:user-id route-params)
                        (get-in route-params [:user :db/id]))))
        store-id (when (::routes/store-owner roles)
                   (c/parse-long-safe
                     (or (:store-id route-params)
                         (get-in route-params [:store :db/id]))))]
    (when (and (seq roles) (string? email))
      (cond-> {:find    '[?user .]
               :where   '[[?user :user/email ?email]]
               :symbols {'?email email}}
              (some? user-id)
              (db/merge-query {:symbols {'?user user-id}})
              (some? store-id)
              (db/merge-query {:where   '[[?store :store/owners ?owner]
                                          [?owner :store.owner/user ?user]]
                               :symbols {'?store store-id}})))))

(defn is-public-role? [roles]
  (= ::routes/public
     (cond-> roles (not (keyword? roles)) (first))))

(defn authed-for-roles? [db roles auth route-params]
  (boolean (or (is-public-role? roles)
               (when-let [query (auth-query roles auth route-params)]
                 (debug "query: " query " role: " roles)
                 (db/find-with db query)))))

(defn bidi-route-restrictions [route]
  (let [auth-roles (routes/auth-roles route)
        redirect-route (routes/redirect-route route)]
    {:handler  (fn [{:keys [identity route-params] :as request}]
                 (let [auth-val {:route        route
                                 :route-params route-params
                                 :auth-roles   auth-roles
                                 :auth         identity}]
                   (if (authed-for-roles? (db/db (:eponai.server.middleware/conn request))
                                          auth-roles
                                          identity
                                          route-params)
                     (buddy/success auth-val)
                     (buddy/error auth-val))))
     :on-error (fn [request v]
                 (logout (assoc request ::redirect-route redirect-route)))}))

;; WHERE AM I?