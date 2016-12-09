(ns eponai.server.middleware
  (:require
    [cemerick.friend :as friend]
    [cemerick.friend.workflows :as friend-wf]
    [buddy.core.codecs.base64 :as b64]
    [buddy.core.codecs :as codecs]
    [eponai.server.auth.credentials :as ac]
    [cognitect.transit :as transit]
    [environ.core :refer [env]]
    [eponai.common.parser.util :as parser.util]
    [eponai.server.http :as h]
    [buddy.auth.backends.session :refer [session-backend]]
    [buddy.auth.backends :as buddy-backends]
    [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
    [eponai.server.auth.workflows :as workflows]
    [om.next.server :as om]
    [ring.middleware.defaults :as r]
    [ring.middleware.gzip :as gzip]
    [ring.middleware.json :refer [wrap-json-body
                                  wrap-json-response]]
    [ring.middleware.ssl :as ssl]
    [ring.middleware.transit :refer [wrap-transit-response
                                     wrap-transit-body]]
    [taoensso.timbre :refer [debug error trace]])
  (:import (datomic.query EntityMap)))

(defn wrap-timing [handler]
  (fn [request]
    (parser.util/timeit "Request timer" (handler request))))

(defn wrap-ssl [handler]
  (comment
    ;; TODO
    "Enable this again when we have a certificate for sulo.live"
    (-> handler
        ;; Redirects to ssl request is not https
        ssl/wrap-ssl-redirect
        ;; Sets request to be https if http header says so.
        ;; The http header will say so when we're behind an amazon load balancer (production).
        ssl/wrap-forwarded-scheme
        ;; This ensures the browser will only use HTTPS for future requests to the domain.
        ;; The value 86400 (24 hours) is taken from what instagram.com uses.
        (ssl/wrap-hsts {:max-age 86400 :include-subdomains? false})))
  handler)

(defn wrap-error [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable e
        (error "Error for request: " request " message: " (.getMessage e))
        (error e)
        (let [error (ex-data e)
              code (h/error-codes (or (:status error) ::h/internal-error))]
          {:status code :body error})))))

(def datomic-transit
  (transit/write-handler
    (constantly "map")
    #(into {:db/id (:db/id %)} %)))

(defn wrap-json [handler]
  (-> handler
      (wrap-json-body {:keywords? true})
      wrap-json-response))

(defn wrap-transit [handler]
  (-> handler
      wrap-transit-body
      (wrap-transit-response {:opts     {:handlers {EntityMap datomic-transit}}
                              :encoding :json})))

(defn wrap-format [handler]
  (fn [r]
    (let [content-type (:content-type r)]
      ;(debug "Found content type: " content-type)
      (if (and (some? content-type)
               (re-find #"application/json" content-type))
        (do
          ;(debug "Wrapping JSON request.")
          ((wrap-json handler) r))
        (do
          ;(debug "Wrapping transit request")
          ((wrap-transit handler) r))))))

(defn wrap-post-middlewares [handler]
  (fn [request]
    (trace "Request after middlewares:" request)
    (handler request)))

(defn wrap-trace-request [handler]
  (fn [request]
    (trace "Request:" request)
    (let [response (handler request)]
      (trace "Response: " response)
      response)))

(defn wrap-state [handler opts]
  (fn [request]
    (handler (merge request opts))))

(defn wrap-gzip [handler]
  (gzip/wrap-gzip handler))

(defn wrap-authenticate [handler conn in-prod?]
  (debug "Wrap authenticate")
  (let [auth-fn (fn [req {:keys [username password] :as token}]
                  (debug "Authfn for request: ")
                  (debug "Token: " token)
                  ;(debug "Wiall auth: " (and (= password "hejsan") (= username "sulo")))
                  (when (and (= password "hejsan") (= username "sulo"))
                    {:user "admin"}))
        unauthed-fn (fn [req metadata]
                      (debug "Unauthed: " metadata)
                      (debug "REQUEST: " req)

                      {:status 200
                       :headers {}
                       :body {}})
        client-secret (apply str (replace {\_ \/ \- \+} (seq (env :auth0-secret))))

        ;base64decoder (Base64/getUrlDecoder)
        ;s_bytes (.getBytes (env :auth0-scret) StandardCharsets/UTF_8)
        auth-backend (buddy-backends/jws {:authfn               auth-fn
                                          :unauthorized-handler unauthed-fn
                                          :secret               (b64/decode client-secret) ;(codecs/bytes->str (b64/decode client-secret))
                                          :token-name           "Bearer"
                                          :on-error             (fn [r e] (debug "Error decrypting")
                                                                  (error e))
                                          ;:options              {:enc :hs256}
                                          })

        ;url-decoder (let [base64decoder (java.util.Base64/getUrlDecoder)
        ;                          s_bytes (.getBytes client-secret java.nio.charset.StandardCharsets/UTF_8)]
        ;                   )
        basic-backend (buddy-backends/http-basic {:authfn auth-fn
                                                  ;:unauthorized-handler unauthed-fn
                                                  :realm "Demo"})
        wrap-auth (fn [h]
                    (fn [req]
                      ;(debug "Authenticate request: " req)
                      (let [ret ((wrap-authentication h auth-backend basic-backend) req)]
                        ;(debug "Authentication response: " ret)
                        ret)))]
    (-> handler
        ;(friend/authenticate {:credential-fn (ac/simple-credential-fn)
        ;                      :workflows [(workflows/simple)
        ;                                  (workflows/auth0)]
        ;                      :unauthenticated-handler (workflows/unauthenticated)})
        ;(wrap-authentication auth-backend)
        (wrap-authorization auth-backend)
        (wrap-auth)
        ))
  ;(friend/authenticate
  ;  handler
  ;  {
  ;   :allow-anon?          true
  ;   ;:credential-fn        (ac/simple-credential-fn)
  ;   :workflows            [(cemerick.friend.workflows/http-basic :credential-fn (ac/simple-credential-fn) :realm "Friend demo")
  ;                          (workflows/auth0)]
  ;   :unauthenticated-handler #(cemerick.friend.workflows/http-basic-deny "Friend demo" %)
  ;   ;:unauthorized-handler (workflows/unauthorized)
  ;   ;:unauthenticated-handler (workflows/unauthenticated)
  ;   ;:uri "/api
  ;   ;:login-uri            "/signup"
  ;   ;:default-landing-uri  "/app"
  ;   ;:fb-login-uri         "/api/login/fb"
  ;   ;:email-login-uri      "/api/login/email"
  ;   ;:activate-account-uri "/api/login/create"
  ;   ;:login-mutation-uri   "/api"
  ;   })
  )

(defn config [in-prod? disable-anti-forgery]
  {:pre [(contains? env :session-cookie-store-key)
         (contains? env :session-cookie-name)]}
  (let [conf (-> r/site-defaults
                 ;(assoc-in [:session :store] (cookie/cookie-store {:key (env :session-cookie-store-key)}))
                 ;(assoc-in [:session :cookie-name] (env :session-cookie-name))
                 ;(assoc-in [:session :cookie-attrs :max-age] 7776000)
                 (assoc-in [:security :anti-forgery] (and false
                                                          (if in-prod?
                                                            true
                                                            (not disable-anti-forgery))))
                 (assoc-in [:static :resources] false))]
    (cond-> conf
            in-prod?
            (assoc-in [:session :cookie-attrs :secure] true))))

(defn wrap-defaults [handler in-prod? disable-anti-forgery]
  (r/wrap-defaults handler (config in-prod? disable-anti-forgery)))

;;TODO: fix this ffs
;(defn wrap-login-parser [handler]
;  (fn [request]
;    (let [parser (om/parser
;                   {:mutate (fn [_ _ p]
;                              {:action (fn [] p)})
;                    :read   (fn [_ _ _])})]
;      (handler (assoc request
;                 :login-parser parser)))))

;(defn wrap-logout [handler]
;  (fn [req]
;    (let [parser (om/parser
;                   {:mutate (fn [_ k p]
;                              {:action (fn []
;                                         (cond (= k 'session/signout)
;                                               {:signout true}))})
;                    :read   (fn [_ _ _])})
;          parsed-res (parser {} (:query (:body req)))
;          signout? (get-in parsed-res ['session/signout :result :signout])]
;      ;(if signout?
;      ;  ((friend/logout handler) req))
;      (handler req)
;      )))