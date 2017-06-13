(ns eponai.server.middleware
  (:require
    [cognitect.transit :as transit]
    [clojure.string :as str]
    [environ.core :refer [env]]
    [ring.util.request :as ring.request]
    [ring.util.response :as ring.response]
    [manifold.deferred :as deferred]
    [aleph.middleware.util :as aleph.m]
    [aleph.middleware.cookies]
    [aleph.middleware.session]
    [aleph.middleware.content-type]
    [ring.middleware.x-headers :as x-headers]
    [ring.middleware.defaults :as r]
    [ring.middleware.json :as ring-json]
    [ring.middleware.ssl :as ssl]
    [ring.middleware.transit :as ring-transit]
    [datascript.transit]
    [taoensso.timbre :refer [debug error trace]]
    [eponai.client.utils :as client.utils]
    [eponai.common.parser.util :as parser.util]
    [eponai.common.database :as db]
    [eponai.common.datascript :as common.datascript]
    [eponai.server.auth :as auth]
    [eponai.server.http :as h]
    [eponai.server.datomic.query :as query]
    ;; Debug/dev require
    [prone.middleware :as prone]
    [prone.debug]
    [cheshire.core :as json]
    [medley.core :as medley])
  (:import (datomic.query EntityMap)))

(defn wrap-timing [handler]
  (fn [request]
    (parser.util/timeit "Request timer" (handler request))))

(defn defer-response-fn [handler response-fn & response-fn-args]
  (fn [request]
    (deferred/let-flow [response (handler request)]
                       (apply response-fn response response-fn-args))))

(defn wrap-ssl [handler]
  (-> handler
      ;; Redirects to ssl request is not https
      ssl/wrap-ssl-redirect
      ;; Sets request to be https if http header says so.
      ;; The http header will say so when we're behind an amazon load balancer (production).
      ssl/wrap-forwarded-scheme
      ;; This ensures the browser will only use HTTPS for future requests to the domain.
      ;; The value 86400 (24 hours) is taken from what instagram.com uses.
      (defer-response-fn ring.response/header "Strict-Transport-Security" (#'ssl/build-hsts-header {:max-age 86400 :include-subdomains? false}))))

(defn wrap-error [handler in-prod?]
  (letfn [(wrap-error-prod [handler]
            (fn [request]
              (-> (handler request)
                  (deferred/catch Throwable
                    (fn [e]
                      (error "Error for request: " (into {} request) " message: " (.getMessage e))
                      (error e)
                      (let [error (ex-data e)
                            code (h/error-codes (or (:status error) ::h/internal-error))]
                        {:status code :body error}))))))
          (wrap-prone-aleph [handler]
            (fn [request]
              (-> (handler request)
                  (deferred/catch Throwable
                    (fn [e]
                      (.printStackTrace e)
                      (binding [prone.debug/*debug-data* (atom [])]
                        (prone/exceptions-response request e '[eponai])))))))]
    (if in-prod?
      (wrap-error-prod handler)
      (wrap-prone-aleph handler))))

(def datomic-transit
  (transit/write-handler
    (constantly "map")
    #(into {:db/id (:db/id %)} %)))

(defn wrap-json [handler]
  (letfn [(deferred-json-response [handler & [{:as options}]]
            (defer-response-fn handler (fn [response]
                                         (if (coll? (:body response))
                                           (let [json-response (update-in response [:body] json/generate-string options)]
                                             (if (contains? (:headers response) "Content-Type")
                                               json-response
                                               (ring.response/content-type json-response "application/json; charset=utf-8")))
                                           response))))]
    (-> handler
        (ring-json/wrap-json-body {:keywords? true})
        (deferred-json-response))))

(defn wrap-transit [handler]
  (letfn [(deferred-transit-response [handler]
            (fn [request]
              (deferred/let-flow
                [response (handler request)]
                (ring-transit/transit-response
                     response request
                     (#'ring-transit/transit-response-options
                       {:opts     {:handlers (merge {EntityMap datomic-transit}
                                                    datascript.transit/write-handlers)}
                        :encoding :json})))))]
    (-> handler
        (ring-transit/wrap-transit-body)
        (deferred-transit-response))))



(defn wrap-format [handler]
  (let [json-request? (fn [request]
                        (when-let [type (ring.response/get-header request "Content-Type")]
                          (not (empty? (re-find #"application/json" type)))))
        json-wrapper (wrap-json handler)
        transit-wrapper (wrap-transit handler)]
    (fn [request]
      (if (json-request? request)
        (json-wrapper request)
        (transit-wrapper request)))))

(defn wrap-post-middlewares [handler]
  (fn [request]
    (trace "Request after middlewares:" (into {} request))
    (handler request)))

(defn wrap-trace-request [handler]
  (fn [request]
    (trace "Request:" (into {} request))
    (deferred/let-flow [response (handler request)]
                       (trace "Response: " (into {} response))
                       response)))

(defn wrap-state [handler state]
  (fn [request]
    (handler (merge request state))))

(defn wrap-authenticate [handler conn auth0]
  (auth/wrap-auth handler conn auth0))

(defn config [in-prod? disable-anti-forgery]
  (let [conf (-> r/site-defaults
                 (assoc-in [:security :anti-forgery] (and false
                                                          (if in-prod?
                                                            true
                                                            (not disable-anti-forgery)))))]
    (cond-> conf
            in-prod?
            (assoc-in [:session :cookie-attrs :secure] true))))

(defn defer-x-headers [handler config]
  (let [xss-header (when-let [xss-options (:xss-protection config)]
                     ["X-XSS-Protection" (str (if (:enable? xss-options true) "1" "0")
                                              (if (dissoc xss-options :enable?) "; mode=block"))])

        frame-options (when-let [frame-options (:frame-options config)]
                        ["X-Frame-Options" (#'x-headers/format-frame-options frame-options)])

        content-type-options (when-let [content-type-options (:content-type-options config)]
                               ["X-Content-Type-Options" (name content-type-options)])]
    (letfn [(add-header [response header]
              (if header
                (apply ring.response/header response header)
                response))]
      (fn [request]
        (deferred/let-flow
          [response (handler request)]
          (-> response
              (add-header xss-header)
              (add-header frame-options)
              (add-header content-type-options)))))))

(defn wrap-aleph-replacements [handler config]
  ;; copy of r/wrap which is private.
  (letfn [(wrap [handler middleware options]
            (if (true? options)
              (middleware handler)
              (cond-> handler options (middleware options))))]
    (-> handler
        (wrap aleph.middleware.cookies/wrap-cookies (get-in config [:cookies]))
        (wrap aleph.middleware.session/wrap-session (get-in config [:session]))
        (wrap aleph.middleware.content-type/wrap-content-type (get-in config [:responses :content-types]))
        (wrap defer-x-headers (get-in config [:security])))))

(defn wrap-defaults [handler in-prod? disable-anti-forgery]
  (let [conf (config in-prod? disable-anti-forgery)]
    (-> handler
        (r/wrap-defaults (-> conf
                             (assoc-in [:cookies] false)
                             (assoc-in [:session] false)
                             (assoc-in [:responses :content-types] false)
                             (update-in [:security] dissoc
                                        :xss-protection
                                        :frame-options
                                        :content-type-options)))
        (wrap-aleph-replacements conf))))

(defn wrap-node-modules [handler]
  (fn [request]
    (let [path (subs (ring.request/path-info request) 1)
          [p & ps] (str/split path #"/")]
      (if (contains? #{"bower_components" "node_modules"} p)
        (ring.response/resource-response (str/join "/" ps))
        (handler request)))))

(defn init-datascript-db [conn]
  (common.datascript/init-db (query/schema (db/db conn)) (client.utils/initial-ui-state)))

(defn wrap-js-files [handler cljs-build-id]
  (fn [request]
    (let [path (ring.request/path-info request)]
      (if-not (str/starts-with? path "/js")
        (handler request)
        (ring.response/resource-response (str "/" cljs-build-id path) {:root "public"})))))