(ns eponai.server.http
  (:require [cemerick.friend.util :as util]
            [environ.core :refer [env]]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.transit :refer [wrap-transit-response wrap-transit-body]]
            [ring.middleware.defaults :as d]
            [ring.middleware.json :refer [wrap-json-body]]
            [ring.util.response :as r]
            [cognitect.transit :as transit])
  (:import (clojure.lang ExceptionInfo)
           (datomic.query EntityMap)))

(def error-codes {
                  ; Client error codes
                  ::unathorized 401
                  ::unprocessable-entity 422

                  ; Server error codes
                  ::internal-error 500
                  ::service-unavailable 503})

(defn response
  "Create response with the given db and data. Fetches the schema for the given data and
  schema and returns a map of the form {:schema [] :entities []}."
  [body]
  (r/response body))

(defn redirect [path request]
  (r/redirect (util/resolve-absolute-uri path request)))

(defn user-created [request]
  (let [{{username :username} :params} request]
    (r/created (util/resolve-absolute-uri "/login" request) {:username username})))

(defn txs-created [request]
  (let [{body :body} request]
    (r/created (util/resolve-absolute-uri "/user/txs" request) body)))

(defn wrap-error [handler]
  (fn [request]
    (try
      (handler request)
      (catch ExceptionInfo e
        (let [error (select-keys (ex-data e) [:status :cause :message])
              code (error-codes (:status error))]
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

(defn wrap-db [handler conn]
  (fn [request]
    (let [response (handler (assoc request ::conn conn))]
      response)))

(defn wrap-defaults [handler]
  (let [cookie-store-key (env :session-cookie-store-key)
        cookie-name (env :session-cookie-name)]
    (d/wrap-defaults handler (-> d/site-defaults
                                 (assoc-in [:security :anti-forgery] false)
                                 (assoc-in [:session :store] (cookie/cookie-store {:key cookie-store-key}))
                                 (assoc-in [:session :cookie-name] cookie-name)))))
