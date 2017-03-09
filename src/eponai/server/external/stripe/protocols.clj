(ns eponai.server.external.stripe.protocols
  (:require
    [eponai.server.external.stripe.format :as f]
    [eponai.server.http :as h]
    [taoensso.timbre :refer [debug]])
  (:import (com.stripe Stripe)
           (com.stripe.net RequestOptions)
           (com.stripe.model Account Customer Charge Product SKU Order)))

(defprotocol IStripeConnect
  (create-account [this opts]
    "Create a managed account on Stripe for a a seller.
    Opts is a map with following keys:
    :country - A two character string code for the country of the seller, e.g. 'US'.")

  (get-account [this account-id]
    "Get a managed account for a seller from Stripe.
    Opts is a map with following keys:
    :country - A two character string code for the country of the seller, e.g. 'US'.")

  (create-customer [this account-id opts])

  (charge [this opts]))

(defprotocol IStripeAccount
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
  (update-order [this account-secret order-id params]))

(defn set-api-key [api-key]
  (if (some? api-key)
    (set! (. Stripe apiKey) api-key)
    (throw (ex-info "No Api key provided" {:message "No API key provided"
                                           :cause   ::h/unprocessable-entity}))))

(defn request-options [account-id]
  (.setStripeAccount (RequestOptions/builder) account-id))

(defrecord StripeRecord [api-key]
  IStripeConnect
  (charge [_ {:keys [amount currency source destination]}]
    (let [params {"amount"      amount
                  "currency"    currency
                  "source"      source
                  "destination" destination}
          charge (Charge/create params)]
      (debug "Created charge: " charge)))

  (get-account [_ account-id]
    (set-api-key api-key)
    (let [account (Account/retrieve ^String account-id)
          external-accounts (.getExternalAccounts account)]
      {:id      (.getId account)
       :country (.getCountry account)}))

  (create-customer [_ account-id {:keys [email]}]
    (let [customer (Customer/create {"email" email} ^RequestOptions (request-options account-id))]
      customer))

  (create-account [_ {:keys [country]}]
    (set-api-key api-key)
    (let [account (Account/create {"country" country
                                   "managed" true})
          keys (.getKeys account)]
      {:id     (.getId account)
       :secret (.getSecret keys)
       :publ   (.getPublishable keys)}))
  IStripeAccount
  (list-products [_ account-secret {:keys [ids]}]
    (set-api-key account-secret)
    (let [params (when (not-empty ids) {"ids" ids})
          products (Product/list params)]
      (map f/stripe->product (.getData products))))

  (create-product [_ account-secret params]
    (set-api-key account-secret)
    (let [new-product (Product/create (f/input->product params))]
      (f/stripe->product new-product)))

  (create-sku [_ account-secret product-id sku]
    (set-api-key account-secret)
    (let [SKU (SKU/create (f/input->sku product-id sku))]
      (f/stripe->sku SKU)))

  (update-sku [_this account-secret sku-id {:keys [quantity value price]}]
    (set-api-key account-secret)
    (let [params (cond-> {"inventory" {"type" "infinite"}}
                         (some? quantity)
                         (assoc-in ["inventory" "quantity"] quantity)
                         (some? quantity)
                         (assoc-in ["inventory" "type"] "finite")
                         (some? value)
                         (assoc-in ["attributes" "variation"] value)
                         (some? price)
                         (assoc "price" (f/input->price price)))
          old-sku (SKU/retrieve sku-id)
          new-sku (.update old-sku params)]
      (f/stripe->sku new-sku)))

  (-update-product [_ account-secret product-id params]
    (set-api-key account-secret)
    (let [new-params {"name" (:name params)}
          old-product (Product/retrieve product-id)
          new-product (.update old-product new-params)]
      (f/stripe->product new-product)))

  (delete-product [_ account-secret product-id]
    (set-api-key account-secret)
    (let [product (Product/retrieve (str product-id))
          deleted (.delete product)]
      {:id      (.getId deleted)
       :deleted (.getDeleted deleted)}))

  (delete-sku [_ account-secret sku-id]
    (set-api-key account-secret)
    (let [sku (SKU/retrieve sku-id)
          deleted (.delete sku)]
      {:id      (.getId deleted)
       :deleted (.getDeleted deleted)}))

  ;; Orders
  (get-order [_ account-secret order-id]
    (set-api-key account-secret)
    (let [order (Order/retrieve order-id)]
      (f/stripe->order order)))

  (list-orders [_ account-secret {:keys [ids]}]
    (set-api-key account-secret)
    (let [params (when (not-empty ids) {"ids" ids})
          orders (Order/list params)]
      (map f/stripe->order (.getData orders))))

  (create-order [_ account-secret order]
    (set-api-key account-secret)
    (let [params (merge {"shipping" {"address" {"city"        nil
                                                "country"     nil
                                                "line1"       nil
                                                "line2"       nil
                                                "postal_code" nil
                                                "state"       nil}
                                     "name"    "This is my name"}}
                        (clojure.walk/stringify-keys order))
          new-order (Order/create params)
          ]
      (debug "Create order with params: " params)
      (f/stripe->order new-order)
      ;{:id     (.getId new-order)
      ; :amount (.getAmount new-order)}
      ))

  (pay-order [_ account-secret order-id source]
    (set-api-key account-secret)
    (let [order (Order/retrieve order-id)
          paid-order (.pay order {"source" source})]
      (f/stripe->order paid-order)))

  (update-order [_ account-secret order-id {:order/keys [status]}]
    (set-api-key account-secret)
    (let [params {"status" (name status)}
          order (Order/retrieve order-id)
          updated (.update order params)]
      (debug "Stripe - Updated order: " order)
      (f/stripe->order updated))))