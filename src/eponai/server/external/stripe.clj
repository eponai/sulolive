(ns eponai.server.external.stripe
  (:require
    [clojure.spec :as s]
    [eponai.common.database :as db]
    [eponai.server.external.stripe.format :as f]
    [eponai.server.http :as h]
    [eponai.server.external.stripe.specs :as specs]
    [taoensso.timbre :refer [debug error info]])
  (:import
    (com.stripe Stripe)
    (com.stripe.model Customer Card Charge Account Refund CountrySpec)
    (com.stripe.net RequestOptions)))

(defn pull-stripe [db store-id]
  (when store-id
    (db/pull-one-with db '[*] {:where   '[[?s :store/stripe ?e]]
                               :symbols {'?s store-id}})))
;; ########## Stripe protocol ################

(defprotocol IStripeConnect
  (-get-country-spec [this code])

  (-create-account [this opts]
    "Create a managed account on Stripe for a a seller.
    Opts is a map with following keys:
    :country - A two character string code for the country of the seller, e.g. 'US'.")

  (-get-account [this account-id]
    "Get a managed account for a seller from Stripe.
    Opts is a map with following keys:
    :country - A two character string code for the country of the seller, e.g. 'US'.")

  (-update-account [this account-id params])

  (-create-customer [this account-id opts])
  ;; Charges
  (-create-charge [this params])
  (-create-refund [this params]))


(defn- set-api-key [api-key]
  (if (some? api-key)
    (set! (. Stripe apiKey) api-key)
    (throw (ex-info "No Api key provided" {:message "No API key provided"
                                           :cause   ::h/unprocessable-entity}))))
(defn- request-options [account-id]
  (.setStripeAccount (RequestOptions/builder) account-id))

;; ############# Public #################
(defn get-country-spec [stripe code]
  (-get-country-spec stripe code))

(defn get-account [stripe account-id]
  (-get-account stripe account-id))

(defn create-account [stripe params]
  (-create-account stripe params))

(defn update-account [stripe account-id params]
  (let [account (f/input->account-params params)]
    (debug "Update account params: " params)
    (debug "Update account: " account)
    (s/assert :ext.stripe.params/update-account account)
    (-update-account stripe account-id account)))

(defn create-charge [stripe params]
  (debug "Create charge: " params)
  (-create-charge stripe params))

(defn create-refund [stripe params]
  (-create-refund stripe params))


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

;; ############## Stripe record #################

(defrecord StripeRecord [api-key]
  IStripeConnect
  (-get-country-spec [_ code]
    (set-api-key api-key)
    (f/stripe->country-spec (CountrySpec/retrieve code)))

  (-get-account [_ account-id]
    (set-api-key api-key)
    (let [account (Account/retrieve ^String account-id)
          external-accounts (.getExternalAccounts account)]
      (f/stripe->account account)))

  (-update-account [_ account-id params]
    (set-api-key api-key)
    (let [account (Account/retrieve ^String account-id)
          updated (.update account (clojure.walk/stringify-keys params))]
      (f/stripe->account updated)))

  (-create-customer [_ account-id {:keys [email]}]
    (set-api-key api-key)
    (let [customer (Customer/create {"email" email} ^RequestOptions (request-options account-id))]
      customer))

  (-create-account [_ {:keys [country]}]
    (set-api-key api-key)
    (let [account (Account/create {"country" country
                                   "managed" true})
          keys (.getKeys account)]
      {:id     (.getId account)
       :secret (.getSecret keys)
       :publ   (.getPublishable keys)}))

  (-create-charge [_ params]
    (set-api-key api-key)
    (let [params (clojure.walk/stringify-keys params)
          charge (Charge/create params)]
      (debug "Created charge: " charge)
      {:charge/status (.getStatus charge)
       :charge/id     (.getId charge)
       :charge/paid?  (.getPaid charge)}))

  (-create-refund [_ {:keys [charge] :as params}]
    (set-api-key api-key)
    (let [params {"charge"           charge
                  "reverse_transfer" true}
          refund (Refund/create params)]
      (debug "Created refund: " refund)
      {:refund/status (.getStatus refund)
       :refund/id     (.getId refund)})))


;; ################## Public ##################

(defn stripe [api-key]
  (->StripeRecord api-key))

(defn stripe-stub []
  (reify IStripeConnect
    (-create-account [_ params]
      (debug "DEV - Fake Stripe: create-account with params: " params))

    (-get-account [_ params]
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

    (-update-account [_ account-id params])
    (-create-customer [_ _ params]
      (debug "DEV - Fake Stripe: create-customer with params: " params))

    ;; Charge
    (-create-charge [this params])
    (-create-refund [this params])))
