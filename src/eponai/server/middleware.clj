(ns eponai.server.middleware
  (:require [cognitect.transit :as transit]
            [environ.core :refer [env]]
            [eponai.server.http :as h]
            [ring.middleware.defaults :as r]
            [ring.middleware.gzip :as gzip]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.transit :refer [wrap-transit-response
                                             wrap-transit-body]])
  (:import (clojure.lang ExceptionInfo)
           (datomic.query EntityMap)))

(defn wrap-error [handler]
  (fn [request]
    (try
      (handler request)
      (catch ExceptionInfo e
        (let [error (select-keys (ex-data e) [:status :cause :message])
              code (h/error-codes (:status error))]
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

(defn wrap-log [handler]
  (fn [request]
    (println "Request " request)
    (let [response (handler request)]
      (println "\nResponse: " response)
      response)))

(defn wrap-state [handler opts]
  (fn [request]
    (handler (merge request opts))))

(defn wrap-gzip [handler]
  (gzip/wrap-gzip handler))

(defn wrap-defaults [handler]
  (let [config {:session  {:store       (cookie/cookie-store {:key (env :session-cookie-store-key)})
                           :cookie-name (env :session-cookie-name)}
                :security {:anti-forgery false}}]
    (r/wrap-defaults handler (merge r/site-defaults
                                    config))))