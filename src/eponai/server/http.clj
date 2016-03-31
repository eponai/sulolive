(ns eponai.server.http
  (:require [environ.core :refer [env]]
            [ring.util.response :as r]))

(def error-codes
  {
   ; Client error codes
   ::unathorized 401
   ::unprocessable-entity 422

   ; Server error codes
   ::internal-error 500
   ::service-unavailable 503})


