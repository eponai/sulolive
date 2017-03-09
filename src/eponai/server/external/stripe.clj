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
      (debug "DEV - Fake Stripe: get-acconunt with params: " params))
    (create-customer [_ _ params]
      (debug "DEV - Fake Stripe: create-customer with params: " params))
    p/IStripeAccount
    (get-order [_ _ _])
    (list-orders [_ _ _])))

(defn create-account [stripe params]
  (p/create-account stripe params))

(defn create-product [stripe account-secret params]
  (p/create-product stripe account-secret params))

(defn update-product [stripe account-secret product-id params]
  (p/-update-product stripe account-secret product-id params))

(defn list-products [stripe account-secret opts]
  (p/list-products stripe account-secret opts))

(defn delete-product [stripe account-secret product-id]
  (s/assert :ext.stripe/secret account-secret)
  (s/assert :ext.stripe.product/uuid product-id)
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
                 :id        (.getId card)
                 })})))
;; ###################################### SPEC

(s/def :ext.stripe/secret string?)
(s/def :ext.stripe.product/uuid uuid?)