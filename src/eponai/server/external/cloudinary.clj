(ns eponai.server.external.cloudinary
  (:require
    [clojure.string :as string]
    [clj-http.client :as http]
    [taoensso.timbre :refer [debug]]
    [clojure.data.json :as json]
    [eponai.common.format.date :as date])
  (:import
    (org.apache.commons.codec.digest DigestUtils)))

(defprotocol ICloudinary
  (real-photo-id [this temp-id])
  (upload-dynamic-photo [this {:keys [public_id resource_type url]}]))

(defn rename-url [resource-type]
  (str "https://api.cloudinary.com/v1_1/sulolive/" resource-type "/rename"))

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

(defrecord Cloudinary [api-key api-secret]
  ICloudinary
  (real-photo-id [_ tempid]
    (string/replace tempid #"temp" "real"))
  (upload-dynamic-photo [this {:keys [public_id resource_type url]}]
    (let [real-public-id (real-photo-id this public_id)     ;(string/replace public_id #"temp" "real")
          ;url (rename-url resource_type)
          params {:from_public_id public_id
                  :to_public_id   real-public-id
                  :timestamp      (date/current-secs)
                  :api_key        api-key}
          _ (debug "Cloudinary rename to url: " real-public-id)

          result (http/post (rename-url resource_type) {:form-params (assoc params :signature (sign-params params api-key api-secret))})]
      (debug "Renamed photo: " (json/read-str (:body result)))
      {:photo/path (string/replace url #"temp" "real")
       :photo/id   real-public-id})
    ;dynamic/temp/mkhjmyaolebtu74msslg
    ))