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
  (let [aws-s3 (get-in req [:eponai.server.middleware/system :system/aws-s3])]
    (s3/sign aws-s3)))