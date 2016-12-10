(ns eponai.server.auth
  (:require
    [buddy.auth.accessrules :as buddy]
    [buddy.auth :refer [authenticated?]]
    [buddy.auth.backends :as backends]
    [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
    [buddy.core.codecs.base64 :as b64]
    [environ.core :refer [env]]
    [taoensso.timbre :refer [error debug]]))

(def http-realm "Sulo-Prototype")

(def restrict buddy/restrict)

(defn http-basic-restrict-opts []
  {:handler  authenticated?
   :on-error (fn [a b]
               {:status  401
                :headers {"Content-Type"     "text/plain"
                          "WWW-Authenticate" (format "Basic realm=\"%s\"" http-realm)}})})

(defn jwt-restrict-opts []
  {:handler (fn [req] (debug "Identity: " (:identity req)) true)
   :on-error (fn [& _]
               (debug "Unauthorized api request")
               {:status 401
                :headers {}
                :body "You fucked up"})})

(defn- jwt-backend []
  (backends/jws {:secret     (b64/decode (env :auth0-secret))
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