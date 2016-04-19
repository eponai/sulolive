(ns eponai.server.stripe-test
  (:require
    [clojure.test :refer :all]
    [eponai.common.database.pull :as p]
    [eponai.server.email :as email]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.datomic.format :as f]
    [eponai.server.test-util :refer [new-db]]
    [datomic.api :as d]))

(def user-email "user@email.com")

(def default-customer-id "default-customer-id")
(def default-subscription-id "default-subscription-id")
(def default-period-end 0)

(defn subscription [id status]
  {:stripe.subscription/id      id
   :stripe.subscription/period-end default-period-end
   :stripe.subscription/status  status})


(defmulti test-stripe-action (fn [_ k _] k))
(defmethod test-stripe-action :customer/create
  [status _ _]
  {:stripe/customer     default-customer-id
   :stripe/subscription (subscription default-subscription-id status)})

(defmethod test-stripe-action :subscription/update
  [status _ {:keys [subscription-id]}]
  (subscription subscription-id status))

(defmethod test-stripe-action :subscription/create
  [status _ _]
  (subscription default-subscription-id status))

(defmethod test-stripe-action :subscription/cancel
  [status _ {:keys [subscription-id]}]
  (subscription subscription-id status))

(defn test-stripe [status k params]
  (test-stripe-action status k params))
;### ------------ Webhook Tests ----------

(deftest charge.failed
  (testing "Charge failed, find user and send email."
    (let [{:keys [user]} (f/user-account-map user-email)
          stripe (f/stripe-account (:db/id user) {:stripe/customer default-customer-id})
          conn (new-db [user
                        stripe])
          event {:id "event-id"
                 :type "charge.failed"
                 :data {:object {:customer   default-customer-id}}}
          result (stripe/webhook conn event {::email/send-payment-reminder-fn (fn [email]
                                                                                email)})]
      (is (= result user-email)))))

(deftest customer.deleted
  (testing "Customer deleted event, stripe account should be removed from datomic."
    (let [{:keys [user]} (f/user-account-map user-email)
          stripe (f/stripe-account (:db/id user) {:stripe/customer default-customer-id
                                                  :stripe/subscription (subscription default-subscription-id :active)})
          conn (new-db [user
                        stripe])
          _ (assert (some? (p/lookup-entity (d/db conn) [:stripe/customer default-customer-id])))
          _ (assert (some? (p/lookup-entity (d/db conn) [:stripe.subscription/id default-subscription-id])))
          event {:id "event-id"
                 :type "customer.deleted"
                 :data {:object {:customer   default-customer-id}}}
          _ (stripe/webhook conn event)]
      (is (nil? (p/lookup-entity (d/db conn) [:stripe/customer default-customer-id])))
      (is (nil? (p/lookup-entity (d/db conn) [:stripe.subscription/id default-subscription-id]))))))

(deftest customer.subscription.created
  (testing "Subscription was created, add to the customer in datomic."
    (let [{:keys [user]} (f/user-account-map user-email)
          stripe (f/stripe-account (:db/id user) {:stripe/customer default-customer-id})
          conn (new-db [user
                        stripe])
          period-end 1000
          subscription-id "new-sub"
          event {:id "event-id"
                 :type "customer.subscription.created"
                 :data {:object {:customer   default-customer-id
                                 :id         subscription-id
                                 :status     "active"
                                 :period_end period-end}}}
          _ (stripe/webhook conn event)
          result (p/lookup-entity (d/db conn) [:stripe.subscription/id subscription-id])]
      (is (= (:stripe.subscription/period-end result) (* 1000 period-end)))
      (is (= (:stripe.subscription/id result) subscription-id)))))

(deftest invoice.payment-successful
  (testing "Invoice was paid, should update the subscription entity in datomic and extend the ends-at attribute."
    (let [{:keys [user]} (f/user-account-map user-email)
          stripe (f/stripe-account (:db/id user) (test-stripe :active :customer/create nil))
          conn (new-db [user
                        stripe])
          period-end 1000
          event {:id "event-id"
                 :type "invoice.payment_succeeded"
                 :data {:object {:customer default-customer-id
                                 :period_end period-end}}}
          _ (stripe/webhook conn event)
          result (p/lookup-entity (d/db conn) [:stripe.subscription/id default-subscription-id])]
      (is (= (:stripe.subscription/period-end result) (* 1000 period-end))))))

