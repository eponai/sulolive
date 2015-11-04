(ns flipmunks.budget.http
  (:require [cemerick.friend.util :as util]
            [cemerick.friend :as friend]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.transit :refer [wrap-transit-response]]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.json :refer [wrap-json-body]]
            [ring.util.response :as r]
            [flipmunks.budget.config :as c])
  (:import (clojure.lang ExceptionInfo)))

(def error-codes {
                  ; Client error codes
                  ::unprocessable-entity 422

                  ; Server error codes
                  ::internal-error 500
                  ::service-unavailable 503})

(defn user
  "Current session user information."
  [session]
  (let [user-id (get-in session [::friend/identity :current])]
    (get-in session [::friend/identity :authentications user-id])))

(defn email
  "The session's email from request."
  [request]
  (-> request
      :session
      user
      :username))

(defn redirect [path request]
  (r/redirect (util/resolve-absolute-uri path request)))

(defn response
  "Create response with the given db and data. Fetches the schema for the given data and
  schema and returns a map of the form {:schema [] :entities []}."
  [body]
  (r/response body))

(defn body [schema data]
  {:schema schema
   :entities data})

(defn- wrap-error [handler]
  (fn [request]
    (try
      (handler request)
      (catch ExceptionInfo e
        (let [error (select-keys (ex-data e) [:status :cause :message])
              code (error-codes (:status error))]
          {:status code :body error})))))

(defn wrap [handler]
  (-> handler
      wrap-error
      wrap-transit-response
      (wrap-json-body {:keywords? true})
      (wrap-defaults (-> site-defaults
                         (assoc-in [:security :anti-forgery] false)
                         (assoc-in [:session :store] (cookie/cookie-store {:key (c/config :session-cookie-key)}))
                         (assoc-in [:session :cookie-name] "cookie-name")))))