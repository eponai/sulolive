(ns eponai.server.middleware
  (:require [cognitect.transit :as transit]
            [environ.core :refer [env]]
            [eponai.server.http :as h]
            [ring.middleware.defaults :as r]
            [ring.middleware.gzip :as gzip]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.transit :refer [wrap-transit-response
                                             wrap-transit-body]]
            [ring.middleware.ssl :as ssl]
            [cemerick.friend :as friend]
            [eponai.server.auth.credentials :as ac]
            [eponai.server.auth.workflows :as workflows]
            [eponai.server.email :as e]
            [taoensso.timbre :refer [debug error trace]])

  (:import (clojure.lang ExceptionInfo)
           (datomic.query EntityMap)))

(defn wrap-ssl [handler]
  (-> handler
      ;; Redirects to ssl request is not https
      ssl/wrap-ssl-redirect
      ;; Sets request to be https if http header says so.
      ;; The http header will say so when we're behind an amazon load balancer (production).
      ssl/wrap-forwarded-scheme
      ;; This ensures the browser will only use HTTPS for future requests to the domain.
      ;; The value 15552000 (180 days) is taken from what facebook.com uses.
      (ssl/wrap-hsts {:max-age 15552000})))

(defn wrap-error [handler]
  (fn [request]
    (try
      (handler request)
      (catch ExceptionInfo e
        (error "Request:" request "gave exception:" e)
        (let [error (ex-data e)
              code (h/error-codes (or (:status error) ::h/internal-error))]
          {:status code :body error})))))

(def datomic-transit
  (transit/write-handler
    (constantly "map")
    #(into {:db/id (:db/id %)} %)))

(defn wrap-transit [handler]
  (-> handler
      wrap-transit-body
      (wrap-transit-response {:opts     {:handlers {EntityMap datomic-transit}}
                              :encoding :json})))

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

(defn wrap-authenticate [handler conn]
  (friend/authenticate
    handler {:credential-fn        (ac/credential-fn conn)
             :workflows            [(workflows/form)
                                    (workflows/create-account (e/send-email-fn conn))
                                    (workflows/facebook (env :facebook-app-id)
                                                        (env :facebook-app-secret))]
             :login-uri            "/signup"
             :default-landing-uri  "/"
             :fb-login-uri         "/api/login/fb"
             :email-login-uri      "/api/login/email"
             :activate-account-uri "/api/login/create"}))

(defn config []
  (-> r/site-defaults
      (assoc-in [:session :store] (cookie/cookie-store {:key (env :session-cookie-store-key)}))
      (assoc-in [:session :cookie-name] (env :session-cookie-name))
      (assoc-in [:security :anti-forgery] false)
      (assoc-in [:static :resources] false)))

(defn wrap-defaults [handler]
  (r/wrap-defaults handler (config)))