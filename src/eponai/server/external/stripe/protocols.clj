(ns eponai.server.external.stripe.protocols
  (:require
    [eponai.server.external.stripe.format :as f]
    [eponai.server.http :as h]
    [taoensso.timbre :refer [debug]])
  (:import (com.stripe Stripe)
           (com.stripe.net RequestOptions)
           (com.stripe.model Account Customer Charge CountrySpec)))

(defprotocol IStripeConnect
  (-get-country-spec [this code])

  (create-account [this opts]
    "Create a managed account on Stripe for a a seller.
    Opts is a map with following keys:
    :country - A two character string code for the country of the seller, e.g. 'US'.")

  (get-account [this account-id]
    "Get a managed account for a seller from Stripe.
    Opts is a map with following keys:
    :country - A two character string code for the country of the seller, e.g. 'US'.")

  (update-account [this account-id params])

  (create-customer [this account-id opts]))

(defprotocol IStripeAccount
  ;; Charges
  (-create-charge [this account-secret params]))

(defn set-api-key [api-key]
  (if (some? api-key)
    (set! (. Stripe apiKey) api-key)
    (throw (ex-info "No Api key provided" {:message "No API key provided"
                                           :cause   ::h/unprocessable-entity}))))

(defn request-options [account-id]
  (.setStripeAccount (RequestOptions/builder) account-id))

(defrecord StripeRecord [api-key]
  IStripeConnect
  (-get-country-spec [_ code]
    (set-api-key api-key)
    (f/stripe->country-spec (CountrySpec/retrieve code)))

  (get-account [_ account-id]
    (set-api-key api-key)
    (let [account (Account/retrieve ^String account-id)
          external-accounts (.getExternalAccounts account)]
      (f/stripe->account account)))

  (update-account [_ account-id params]
    (set-api-key api-key)
    (let [account (Account/retrieve ^String account-id)
          updated (.update account (clojure.walk/stringify-keys params))]
      (f/stripe->account updated)))

  (create-customer [_ account-id {:keys [email]}]
    (set-api-key api-key)
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
  (-create-charge [_ account-secret {:keys [amount currency source application-fee] :as params}]
    (set-api-key account-secret)
    (let [params (clojure.walk/stringify-keys params)
          charge (Charge/create params)]
      (debug "Created charge: " charge)
      {:charge/status (.getStatus charge)
       :charge/id     (.getId charge)})))