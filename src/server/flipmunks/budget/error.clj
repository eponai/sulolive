(ns flipmunks.budget.error
  (:require [ring.util.response :as r])
  (:import (clojure.lang ExceptionInfo)))

(def error-codes {
                  ; Client error codes
                  ::unprocessable-entity 422

                  ; Server error codes
                  ::internal-error 500
                  ::service-unavailable 503})

(defn wrap-http-error [handler]
  (fn [request]
    (try
      (handler request)
      (catch ExceptionInfo e
        (let [error (select-keys (ex-data e) [:status :cause :message])
              code (error-codes (:status error))]
          {:status code :body (str error)})))))