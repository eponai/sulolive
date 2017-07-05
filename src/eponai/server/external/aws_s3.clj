(ns eponai.server.external.aws-s3
  (:require
    [amazonica.aws.s3 :as aws-s3]
    [s3-beam.handler :as s3]
    [taoensso.timbre :refer [debug]]
    [clojure.java.io :as io])
  (:import (com.amazonaws.services.s3.model CannedAccessControlList AmazonS3Exception)))

(defprotocol IAWSS3
  (sign [this])

  (convert-to-real-key [this old-key]
    "Convert a temp key into a real key.")
  (move-object [this bucket old-key new-key]
    "Move object from temp folder to real folder.")

  (upload-object [this params]
    "Move object from temp folder to real folder.")

  (download-object [this params to-file]
    "Downloads object to a file on the server"))

(defrecord AwsS3 [bucket access-key secret zone]
  IAWSS3
  (sign [_]
    (s3/s3-sign bucket zone access-key secret))

  (convert-to-real-key [this old-key]
    (clojure.string/join "/" (assoc (clojure.string/split old-key #"/") 1 "real")))

  (move-object [_ bucket old-key new-key]
    (aws-s3/copy-object bucket old-key bucket new-key)
    (aws-s3/set-object-acl bucket new-key CannedAccessControlList/PublicRead)
    (aws-s3/delete-object bucket old-key))

  (upload-object [this {:keys [bucket key] :as p}]
    (try
      ;(debug "Try to upload photo: " p)
      (let [real-key (convert-to-real-key this key)
            s3-upload-url (str "https://s3.amazonaws.com/" bucket "/" real-key)]
        (move-object this bucket key real-key)
        s3-upload-url)
      (catch AmazonS3Exception e
        (throw (ex-info (.getMessage e) {:message (.getMessage e)})))))

  (download-object [this {:keys [bucket key]} to-file]
    (with-open [in (:input-stream (aws-s3/get-object bucket key))
                out (io/output-stream to-file)]
      (io/copy in out))))

(defn aws-s3-stub []
  (reify IAWSS3
    (sign [_])
    (convert-to-real-key [_ _])
    (upload-object [_ _])
    (move-object [_ _ _ _])))