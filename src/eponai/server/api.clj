(ns eponai.server.api
  (:require
    [compojure.core :refer :all]
    [environ.core :refer [env]]
    [eponai.server.http :as http]
    [eponai.server.external.aws-s3 :as s3]
    [taoensso.timbre :refer [debug error info]]
    [amazonica.core :refer [defcredential]]))

(defcredential (env :aws-access-key-id) (env :aws-secret-access-key) (env :aws-s3-bucket-photos-zone))
; Actions

(defn api-error [status code ex-data]
  (ex-info (str "API error code " code)
           (merge {:status status
                   :code code}
                  ex-data)))

(defn aws-s3-sign [req]
  ;(debug "Sign req: " (get-in req [:params :x-amz-meta-size]))
  (let [image-size (Long/parseLong (get-in req [:params :x-amz-meta-size]))
        aws-s3 (get-in req [:eponai.server.middleware/system :system/aws-s3])]
    (if (< image-size 5000000)
      (let [signature (s3/sign aws-s3)]
        (debug "SIGNED: " signature)
        signature)
      (throw (ex-info "Image uploads need to be smaller than 5MB" {:cause ::http/unprocessable-entity
                                                                   :message "Image cannot be larger than 5MB"})))))