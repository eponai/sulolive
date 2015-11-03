(ns flipmunks.budget.http
  (:require [ring.util.response :as r]
            [cemerick.friend.util :as util])
  (:import (clojure.lang ExceptionInfo)))

(def error-codes {
                  ; Client error codes
                  ::unprocessable-entity 422

                  ; Server error codes
                  ::internal-error 500
                  ::service-unavailable 503})

(defn redirect [path request]
  (ring.util.response/redirect (util/resolve-absolute-uri path request)))

(defn response
  "Create response with the given db and data. Fetches the schema for the given data and
  schema and returns a map of the form {:schema [] :entities []}."
  [schema data]
  (r/response {:schema   schema
               :entities data}))

(defn wrap-error [handler]
  (fn [request]
    (try
      (handler request)
      (catch ExceptionInfo e
        (let [error (select-keys (ex-data e) [:status :cause :message])
              code (error-codes (:status error))]
          {:status code :body (str error)})))))