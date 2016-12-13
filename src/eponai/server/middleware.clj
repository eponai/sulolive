(ns eponai.server.middleware
  (:require
    [cognitect.transit :as transit]
    [clojure.string :as str]
    [environ.core :refer [env]]
    [eponai.common.parser.util :as parser.util]
    [eponai.server.http :as h]
    [ring.util.request :as ring.request]
    [ring.util.response :as ring.response]
    [ring.middleware.defaults :as r]
    [ring.middleware.gzip :as gzip]
    [ring.middleware.json :refer [wrap-json-body
                                  wrap-json-response]]
    [ring.middleware.ssl :as ssl]
    [ring.middleware.transit :refer [wrap-transit-response
                                     wrap-transit-body]]
    [taoensso.timbre :refer [debug error trace]]
    [eponai.server.auth :as auth]
    ;; Debug/dev require
    [prone.middleware :as prone])
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

(defn wrap-error [handler in-prod?]
  (letfn [(wrap-error-prod [handler]
            (fn [request]
              (try
                (handler request)
                (catch Throwable e
                  (error "Error for request: " request " message: " (.getMessage e))
                  (error e)
                  (let [error (ex-data e)
                        code (h/error-codes (or (:status error) ::h/internal-error))]
                    {:status code :body error})))))]
    (if in-prod?
      (wrap-error-prod handler)
      (prone/wrap-exceptions handler {:app-namespaces     '[eponai]
                                      :print-stacktraces? true}))))

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
  (auth/wrap-auth handler conn))

(defn config [in-prod? disable-anti-forgery]
  {:pre [(contains? env :session-cookie-store-key)
         (contains? env :session-cookie-name)]}
  (let [conf (-> r/site-defaults
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

(defn wrap-node-modules [handler]
  (fn [request]
    (let [path (subs (ring.request/path-info request) 1)
          [p & ps] (str/split path #"/")]
      (if (= p "node_modules")
        (ring.response/resource-response (str/join "/" ps))
        (handler request)))))