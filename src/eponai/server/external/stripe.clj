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
    (charge [_ params]
      (debug "DEV - Fake Stripe: charge with params: " params))
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
                                   :stripe.external-account/country   "CA"}] ;(map stripe->bank-account (.getExternalAccounts a))
       :stripe/business-name     "My business Name"
       :stripe/business-url      "My business URL"})
    (create-customer [_ _ params]
      (debug "DEV - Fake Stripe: create-customer with params: " params))
    p/IStripeAccount
    ;; Producs
    (create-product [this account-secret product])
    (-update-product [_ account-secret product-id params])
    (delete-product [this account-secret product-id])
    (list-products [this account-secret opts])

    ;; SKUs
    (create-sku [this account-secret product-id sku])
    (update-sku [this account-secret sku-id params])
    (delete-sku [this account-secret sku-id])

    ;; Orders
    (get-order [this account-secret order-id])
    (list-orders [this account-secret params])
    (create-order [this account-secret params])
    (pay-order [this account-secret order-id source])
    (update-order [this account-secret order-id params])))

(defn get-country-spec [stripe code]
  (p/get-country-spec stripe code))

(defn get-account [stripe account-id]
  (p/get-account stripe account-id))

(defn create-account [stripe params]
  (p/create-account stripe params))

(defn update-account [stripe account-id params]
  (s/assert :ext.stripe.params/update-account params)
  (p/update-account stripe account-id params))

(defn create-product [stripe account-secret params]
  (s/assert :ext.stripe.params/create-product params)
  (p/create-product stripe account-secret params))

(defn update-product [stripe account-secret product-id params]
  (s/assert :ext.stripe.product/id product-id)
  (p/-update-product stripe account-secret product-id params))

(defn list-products [stripe account-secret opts]
  (p/list-products stripe account-secret opts))

(defn delete-product [stripe account-secret product-id]
  (s/assert :ext.stripe/secret account-secret)
  (s/assert :ext.stripe.product/id product-id)
  (p/delete-product stripe account-secret product-id))

(defn create-sku [stripe account-secret product-id params]
  (p/create-sku stripe account-secret product-id params))

(defn update-sku [stripe account-secret sku-id params]
  (p/update-sku stripe account-secret sku-id params))

(defn delete-sku [stripe account-secret sku-id]
  (p/delete-sku stripe account-secret sku-id))

(defn create-order [stripe account-secret params]
  (p/create-order stripe account-secret params))

(defn get-order [stripe account-secret order-id]
  (p/get-order stripe account-secret order-id))

(defn pay-order [stripe account-secret order-id source]
  (p/pay-order stripe account-secret order-id source))

(defn update-order [stripe account-secret order-id params]
  (p/update-order stripe account-secret order-id params))

(defn list-orders [stripe account-secret params]
  (p/list-orders stripe account-secret params))

;; ########### Stripe objects ################

(defn customer [customer-id]
  (when customer-id
    (let [^Customer customer (Customer/retrieve customer-id)
          _ (debug "Customer object: " customer)
          default-source (.getDefaultSource customer)
          ^Card card (when default-source
                       (.retrieve (.getSources customer) (.getDefaultSource customer)))
          ]
      {:id    (.getId customer)
       :email (.getEmail customer)
       :card  (when card
                {:exp-month (.getExpMonth card)
                 :exp-year  (.getExpYear card)
                 :name      (.getName card)
                 :last4     (.getLast4 card)
                 :brand     (.getBrand card)
                 :id        (.getId card)})})))

;; ###################################### SPEC

(s/def :ext.stripe/secret string?)

(s/def :ext.stripe.product/id uuid?)
(s/def :ext.stripe.product/name string?)
(s/def :ext.stripe.product/price string?)

(s/def :ext.stripe.sku/id uuid?)

(s/def :ext.stripe.params/create-sku (s/keys :req-un
                                             [:ext.stripe.sku/id]))
(s/def :ext.stripe.params/create-product (s/keys :req-un
                                                 [:ext.stripe.product/id
                                                  :ext.stripe.product/name]))

(s/def :ext.stripe.legal-entity.dob/year number?)
(s/def :ext.stripe.legal-entity.dob/month number?)
(s/def :ext.stripe.legal-entity.dob/day number?)
(s/def :ext.stripe.legal-entity/first_name string?)
(s/def :ext.stripe.legal-entity/last_name string?)
(s/def :ext.stripe.legal-entity/dob (s/keys :req-un [:ext.stripe.legal-entity.dob/year
                                                     :ext.stripe.legal-entity.dob/month
                                                     :ext.stripe.legal-entity.dob/day]))
(s/def :ext.stripe/legal_entity (s/keys :opt-un [:ext.stripe.legal-entity/first_name
                                                 :ext.stripe.legal-entity/last_name
                                                 :ext.stripe.legal-entity/dob]))
(s/def :ext.stripe.params/update-account (s/keys :opt-un
                                                 [:ext.stripe/legal_entity]))