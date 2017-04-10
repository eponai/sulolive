(ns eponai.server.api
  (:require
    [compojure.core :refer :all]
    [datomic.api :as d]
    [environ.core :refer [env]]
    [eponai.common.database :as db]
    [eponai.server.datomic.format :as datomic.format]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.external.mailchimp :as mailchimp]
    [eponai.server.http :as http]
    [eponai.server.external.aws-s3 :as s3]
    [taoensso.timbre :refer [debug error info]]
    [clojure.data.json :as json]
    [amazonica.core :refer [defcredential]]
    [eponai.server.datomic.format :as f])
  (:import (datomic Connection)
           (clojure.lang ExceptionInfo)
           (com.amazonaws.services.s3.model CannedAccessControlList AmazonS3Exception)))

(defcredential (env :aws-access-key-id) (env :aws-secret-access-key) (env :aws-s3-bucket-photos-zone))
; Actions

(defn api-error [status code ex-data]
  (ex-info (str "API error code " code)
           (merge {:status status
                   :code code}
                  ex-data)))

(declare stripe-trial)

(defn stripe-update-card
  [conn stripe-fn {:keys [stripe/customer
                          stripe/subscription]} {:keys [token]}]
  (let [{:keys [id email]} token
        subscription-id (:stripe.subscription/id subscription)]
    (if (some? customer)
      (let [updated-customer (stripe-fn :customer/update
                               {:customer-id customer
                                :params      {"source" id}})
            update-subscription (stripe-fn :subscription/update
                                           {:customer-id customer
                                            :subscription-id subscription-id
                                            :params {"trial_end" "now"}})]
        (debug "Did update stripe customer: " updated-customer)
        (debug "Updated subscription: " update-subscription)
        (db/transact-one conn [:db/add [:stripe.subscription/id subscription-id] :stripe.subscription/status (:stripe.subscription/status update-subscription)]))
      (throw (ex-info (str "Could not find customer for user with email: " email)
                      {:data {:token token}})))))

(defn stripe-trial
  "Subscribe user to trial, without requesting credit carg."
  [conn stripe-fn {:keys [user/email stripe/_user] :as user}]
  {:pre [(instance? Connection conn)
         (fn? stripe-fn)
         (string? email)]}
  (when (seq _user)
    (throw (api-error ::http/unprocessable-entity :illegal-argument
                      {:message       "Cannot start a trial for user that is already a Stripe customer."
                       :function      (str stripe-trial)
                       :function-args {'stripe-fn stripe-fn
                                       'user      user}})))
  (let [{user-id :db/id} (db/pull (d/db conn) [:db/id] [:user/email email])
        stripe (stripe-fn :customer/create
                          {:params {"plan"  "paywhatyouwant"
                                    "email" email
                                    "quantity" 0}})]
    (debug "Starting trial for user: " email " with stripe info: " stripe)
    (assert (some? user-id))
    (when stripe
      (db/transact-one conn (datomic.format/stripe-account user-id stripe)))))

;(defn newsletter-subscribe [conn email]
;  (let [{:keys [verification] :as account} (datomic.format/user-account-map email)]
;    (mailchimp/subscribe (env :mail-chimp-api-key)
;                         (env :mail-chimp-list-id)
;                         email
;                         (:verification/uuid verification))
;    (info "Newsletter subscribe successful, transacting user into datomic.")
;    (comment
;      ;; TODO: Actually transact this if we want to release jour money ^^
;      (transact-map conn account))))


;(defn s3-photo-move [bucket old-key new-key]
;  (aws-s3/copy-object bucket old-key bucket new-key)
;  (aws-s3/set-object-acl bucket new-key CannedAccessControlList/PublicRead)
;  (aws-s3/delete-object bucket old-key))

;(defn s3-photo-real-key [old-key]
;  (clojure.string/join "/" (assoc (clojure.string/split old-key #"/") 1 "real")))

;(defn s3-photo-upload-url [bucket key]
;  (str "https://s3.amazonaws.com/" bucket "/" key))

;(defn upload-user-photo [{:keys [bucket key] :as p}]
;  (try
;    (debug "Try to upload photo: " p)
;    (let [real-key (s3-photo-real-key key)
;          db-photo (f/photo (s3-photo-upload-url bucket real-key))]
;      (s3-photo-move bucket key real-key)
;      db-photo)
;    (catch AmazonS3Exception e
;      (throw (ex-info (.getMessage e) {:message (.getMessage e)})))))

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