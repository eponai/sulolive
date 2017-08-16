(ns eponai.server.external.google
  (:require
    [clj-http.client :as http]
    [taoensso.timbre :refer [debug]]
    [clojure.data.json :as json]))

(defprotocol IGoogle
  (place-details [this place-id]))

(defn google [api-key]
  (reify IGoogle
    (place-details [this place-id]
      (let [details (json/read-str
                      (:body (http/get "https://maps.googleapis.com/maps/api/place/details/json"
                                      {:query-params {:placeid place-id
                                                      :key     api-key}}))
                      :key-fn keyword)]
        (debug "Google place detials: " (:result details))
        (:result details)))))