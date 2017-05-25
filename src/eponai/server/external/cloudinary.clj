(ns eponai.server.external.cloudinary
  (:require
    [clojure.string :as string]
    [clj-http.client :as http]
    [taoensso.timbre :refer [debug]]
    [clojure.data.json :as json]
    [eponai.common.format.date :as date]
    [eponai.common.photos :as photos])
  (:import
    (org.apache.commons.codec.digest DigestUtils)))

(defprotocol ICloudinary
  (real-photo-id [this temp-id])
  (upload-dynamic-photo [this {:keys [public_id resource_type url]}])
  (destroy-photo [this photo-id]))

(defn rename-photo [params]
  (let [resource-type "image"
        endpoint "rename"
        url (string/join "/" [photos/api-host photos/cloud-name resource-type endpoint])]
    (http/post url {:form-params params})))

(defn destroy-photo [params]
  (let [resource-type "image"
        endpoint "destroy"
        url (string/join "/" [photos/api-host photos/cloud-name resource-type endpoint])]
    (http/post url {:form-params params})))

(defn sign-params [params api-key api-secret]
  (let [include-params (->> (dissoc params :api_key :resource_type :type :file)
                            (into (sorted-map)))
        query-string (string/join "&"
                                  (reduce (fn [l [k v]]
                                            (conj l (str (name k) "=" v)))
                                          []
                                          include-params))]
    ;(debug "String will be signed: " query-string)
    ;(debug "Using secret: " api-secret)
    (debug "Cloudinary SHA-1 for string: " (str query-string api-secret))
    (DigestUtils/sha1Hex (str query-string api-secret))))

(defrecord Cloudinary [api-key api-secret uploaded-folder]
  ICloudinary
  (real-photo-id [_ tempid]
    (string/replace tempid #"temp" uploaded-folder))
  (upload-dynamic-photo [this {:keys [public_id resource_type url] :as p}]
    (let [real-public-id (real-photo-id this public_id)
          params {:from_public_id public_id
                  :to_public_id   real-public-id
                  :timestamp      (date/current-secs)
                  :api_key        api-key}
          result (rename-photo (assoc params :signature (sign-params params api-key api-secret)))]
      (debug "Cloudinary: Renamed photo: " (json/read-str (:body result)))
      {:photo/path (string/replace url #"temp" "real")
       :photo/id   real-public-id}))
  (destroy-photo [this photo-id]
    (let [params {:public_id photo-id
                  :timestamp (date/current-secs)
                  :api_key   api-key}
          result (destroy-photo (assoc params :signature (sign-params params api-key api-secret)))]
      (debug "Cloudinary: Destroyed photo: " (json/read-str (:body result))))))

(defn cloudinary-dev [api-key api-secret]
  (->Cloudinary api-key api-secret "dev"))

(defn cloudinary [api-key api-secret in-prod?]
  (if in-prod?
    (->Cloudinary api-key api-secret "real")
    (->Cloudinary api-key api-secret "dev")))