(ns eponai.server.external.aws-s3
  (:require
    [amazonica.aws.s3 :as aws-s3]
    [eponai.server.datomic.format :as f]
    [s3-beam.handler :as s3])
  (:import (com.amazonaws.services.s3.model CannedAccessControlList AmazonS3Exception)))

(defprotocol IAWSS3Photo
  (sign [this])

  (convert-to-real-key [this old-key]
    "Convert a temp key into a real key.")
  (move-photo [this bucket old-key new-key]
    "Move photo from temp folder to real folder.")

  (upload-photo [this params]
    "Move photo from temp folder to real folder."))

(defn aws-s3 [{:keys [bucket access-key secret zone]}]
  (reify IAWSS3Photo
    (sign [_]
      (s3/s3-sign bucket zone access-key secret))

    (convert-to-real-key [this old-key]
      (clojure.string/join "/" (assoc (clojure.string/split old-key #"/") 1 "real")))

    (move-photo [_ bucket old-key new-key]
      (aws-s3/copy-object bucket old-key bucket new-key)
      (aws-s3/set-object-acl bucket new-key CannedAccessControlList/PublicRead)
      (aws-s3/delete-object bucket old-key))

    (upload-photo [this {:keys [bucket key]}]
      (try
        ;(debug "Try to upload photo: " p)
        (let [real-key (convert-to-real-key this key)
              s3-upload-url (str "https://s3.amazonaws.com/" bucket "/" real-key)
              db-photo (f/photo s3-upload-url)]
          (move-photo this bucket key real-key)
          db-photo)
        (catch AmazonS3Exception e
          (throw (ex-info (.getMessage e) {:message (.getMessage e)})))))))

(defn aws-s3-stub []
  (reify IAWSS3Photo
    (sign [_])
    (convert-to-real-key [_ _])
    (upload-photo [_ _])
    (move-photo [_ _ _ _])))