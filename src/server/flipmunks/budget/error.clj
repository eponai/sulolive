(ns flipmunks.budget.error
  (:require [ring.util.response :as r])
  (:import (clojure.lang ExceptionInfo)))

(def error-codes {
                  ; Client error codes
                  ::unprocessable-entity 422

                  ; Server error codes
                  ::internal-error 500})

(defn safe
  "Tries to call fn with the given args, and catches and returns any ExceptionInfo that might be thrown by fn."
  [fn & args]
  (try
    (apply fn args)
    (catch ExceptionInfo e
      e)))

(defn http-error [& data]
  (when-let [error (some identity (map ex-data data))]
    (let [status (error :status)]
      (r/status (select-keys error [:cause :message]) (error-codes status)))))