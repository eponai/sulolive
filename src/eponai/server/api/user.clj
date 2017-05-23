(ns eponai.server.api.user
  (:require
    [eponai.common.database :as db]
    [taoensso.timbre :refer [debug]]
    [eponai.server.external.stripe :as stripe]))

(defn ->order [state o store-id user-id]
  (let [store (db/lookup-entity (db/db state) store-id)]
    (-> o
        (assoc :order/store store :order/user user-id))))

(defn customer [{:keys [auth state system]}]
  (debug "Pull stripe " auth)
  (let [stripe-user (stripe/pull-user-stripe (db/db state) (:user-id auth))]
    (debug "Stripe user: " stripe-user)
    (when stripe-user
      (assoc (stripe/get-customer (:system/stripe system) (:stripe/id stripe-user)) :db/id (:db/id stripe-user)))))