(ns eponai.server.stripe-test
  (:require
    [clojure.test :refer :all]
    [eponai.common.database :as db]
    [eponai.server.email :as email]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.datomic.format :as f]
    [eponai.server.test-util :refer [new-db]]
    [datomic.api :as d]))

(def user-email "user@email.com")

(def default-customer-id "default-customer-id")
(def default-subscription-id "default-subscription-id")
(def default-period-end 0)

(defn new-db-subscription [id status]
  {:stripe.subscription/id      id
   :stripe.subscription/period-end default-period-end
   :stripe.subscription/status  status})


(defmulti test-stripe-action (fn [_ k _] k))
(defmethod test-stripe-action :customer/create
  [status _ _]
  {:stripe/customer     default-customer-id
   :stripe/subscription (new-db-subscription default-subscription-id status)})

(defmethod test-stripe-action :customer/update
  [status _ _]
  {:stripe/customer default-customer-id})

(defmethod test-stripe-action :subscription/update
  [status _ {:keys [subscription-id]}]
  (new-db-subscription subscription-id status))

(defmethod test-stripe-action :subscription/create
  [status _ _]
  (new-db-subscription default-subscription-id status))

(defmethod test-stripe-action :subscription/cancel
  [status _ {:keys [subscription-id]}]
  (new-db-subscription subscription-id status))

(defn test-stripe [status k params]
  (test-stripe-action status k params))

;### ------------ Webhook Tests ----------

