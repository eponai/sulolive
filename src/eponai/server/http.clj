(ns eponai.server.http
  (:require [cemerick.friend.util :as util]
            [environ.core :refer [env]]
            [ring.util.response :as r]))

(def error-codes
  {
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

(defn redirect [path]
  (r/redirect path))

