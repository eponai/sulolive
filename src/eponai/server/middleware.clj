(ns eponai.server.middleware
  (:require [cognitect.transit :as transit]
            [environ.core :refer [env]]
            [eponai.server.http :as h]
            [ring.middleware.defaults :as r]
            [ring.middleware.gzip :as gzip]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.transit :refer [wrap-transit-response
                                             wrap-transit-body]]
            [cemerick.friend :as friend]
            [eponai.server.auth.credentials :as ac]
            [eponai.server.auth.workflows :as aw])

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

(defn wrap-authenticate [handler conn]
  (friend/authenticate
    handler {:credential-fn       (ac/credential-fn conn)
             :workflows           [(aw/default-flow)]
             :default-landing-uri "/budget"}))

(defn config []
  (-> r/site-defaults
      (assoc-in [:session :store] (cookie/cookie-store {:key (env :session-cookie-store-key)}))
      (assoc-in [:session :cookie-name] (env :session-cookie-name))
      (assoc-in [:security :anti-forgery] false)
      (assoc-in [:static :resources] false)))

(defn wrap-defaults [handler]
  (r/wrap-defaults handler (config)))