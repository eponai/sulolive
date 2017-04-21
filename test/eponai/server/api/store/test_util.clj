(ns eponai.server.api.store.test-util
  (:require
    [eponai.server.external.aws-s3 :as s3]
    [eponai.common.database :as db]
    [clojure.core.async :as async]
    [eponai.server.external.stripe :as stripe]))

(defn s3-test [chan]
  (reify s3/IAWSS3Photo
    (convert-to-real-key [this old-key])
    (move-photo [this bucket old-key new-key])
    (upload-photo [this params]
      (let [{:keys [location]} params]
        (async/put! chan location)
        location))))

(defn stripe-test-payment-succeeded [chan]
  (reify stripe/IStripeAccount
    (-create-charge [_ _ params]
      (async/put! chan params)
      {:charge/id    (:source params)
       :charge/paid? true})
    (-create-refund [_ _ {:keys [charge] :as params}]
      (async/put! chan params)
      {:refund/charge charge})))

(defn stripe-test-payment-failed [chan]
  (reify stripe/IStripeAccount
    (-create-charge [_ _ params]
      (async/put! chan params)
      {:charge/id    (:source params)
       :charge/paid? false})
    (-create-refund [_ _ {:keys [charge] :as params}]
      (async/put! chan params)
      {:refund/charge charge})))

(defn store-test []
  {:db/id        (db/tempid :db.part/user)
   :store/stripe {:db/id         (db/tempid :db.part/user)
                  :stripe/secret "stripe-secret"}
   :store/uuid   (db/squuid)})

(defn user-test []
  {:db/id       (db/tempid :db.part/user)
   :user/stripe {:db/id     (db/tempid :db.part/user)
                 :stripe/id "customer-id"}
   :user/email  "dev@sulo.live"})