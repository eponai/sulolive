(ns eponai.server.external.stripe
  (:require
    [clojure.spec :as s]
    [eponai.common.database :as db]
    [eponai.server.external.stripe.format :as f]
    [eponai.server.http :as h]
    [eponai.server.external.stripe.protocols :as p]
    [taoensso.timbre :refer [debug error info]])
  (:import
    (com.stripe Stripe)
    (com.stripe.model Customer Card Charge Subscription Account Product SKU Order ShippingDetails)
    (com.stripe.net RequestOptions)))

(defn pull-stripe [db store-id]
  (when store-id
    (db/pull-one-with db '[*] {:where   '[[?s :store/stripe ?e]]
                               :symbols {'?s store-id}})))
;; ########## Stripe protocol ################

(defn stripe [api-key]
  (p/->StripeRecord api-key))

(defn stripe-stub []
  (reify p/IStripeConnect
    (create-account [_ params]
      (debug "DEV - Fake Stripe: create-account with params: " params))
    (get-account [_ params]
      {:stripe/id                "acct_19k3ozC0YaFL9qxh"
       :stripe/country           "CA"
       :stripe/legal-entity      {:stripe.legal-entity/first-name "First"
                                  :stripe.legal-entity/last-name  "Last"
                                  :stripe.legal-entity/dob        {:stripe.legal-entity.dob/year  1970
                                                                   :stripe.legal-entity.dob/month 1
                                                                   :stripe.legal-entity.dob/day   1}}
       :stripe/external-accounts [{:stripe.external-account/currency  "CAD"
                                   :stripe.external-account/bank-name "Wells Fargo"
                                   :stripe.external-account/last4     "1234"
                                   :stripe.external-account/country   "CA"}]
       :stripe/business-name     "My business Name"
       :stripe/business-url      "My business URL"})
    (create-customer [_ _ params]
      (debug "DEV - Fake Stripe: create-customer with params: " params))
    p/IStripeAccount

    ;; Charge
    (-create-charge [this account-secret params])))

(defn get-country-spec [stripe code]
  (p/-get-country-spec stripe code))

(defn get-account [stripe account-id]
  (p/get-account stripe account-id))

(defn create-account [stripe params]
  (p/create-account stripe params))

(defn update-account [stripe account-id params]
  (let [account (f/input->account-params params)]
    (debug "Update account params: " params)
    (debug "Update account: " account)
    (s/assert :ext.stripe.params/update-account account)
    (p/update-account stripe account-id account)))

(defn create-charge [stripe account-secret params]
  (p/-create-charge stripe account-secret params))

;; ########### Stripe objects ################

(defn customer [customer-id]
  (when customer-id
    (let [^Customer customer (Customer/retrieve customer-id)
          _ (debug "Customer object: " customer)
          default-source (.getDefaultSource customer)
          ^Card card (when default-source
                       (.retrieve (.getSources customer) (.getDefaultSource customer)))]
      {:id    (.getId customer)
       :email (.getEmail customer)
       :card  (when card
                {:exp-month (.getExpMonth card)
                 :exp-year  (.getExpYear card)
                 :name      (.getName card)
                 :last4     (.getLast4 card)
                 :brand     (.getBrand card)
                 :id        (.getId card)})})))

;; ###################################### SPECS

(s/def :ext.stripe.legal-entity.dob/year number?)
(s/def :ext.stripe.legal-entity.dob/month number?)
(s/def :ext.stripe.legal-entity.dob/day number?)
(s/def :ext.stripe.legal-entity/first_name string?)
(s/def :ext.stripe.legal-entity/last_name string?)
(s/def :ext.stripe.legal-entity/type (s/and string? #(contains? #{"individual" "company"} %)))

(s/def :ext.stripe/default_currency (s/and string? #(= 3 (count %))))

(s/def :ext.stripe.legal-entity/dob (s/keys :req-un [:ext.stripe.legal-entity.dob/year
                                                     :ext.stripe.legal-entity.dob/month
                                                     :ext.stripe.legal-entity.dob/day]))

(s/def :ext.stripe/legal_entity (s/keys :opt-un [:ext.stripe.legal-entity/first_name
                                                 :ext.stripe.legal-entity/last_name
                                                 :ext.stripe.legal-entity/type
                                                 :ext.stripe.legal-entity/dob]))

(s/def :ext.stripe.params/update-account (s/keys :opt-un
                                                 [:ext.stripe/legal_entity
                                                  :ext.stripe/default_currency
                                                  :ext.stripe/payout_schedule]))