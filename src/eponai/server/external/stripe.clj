(ns eponai.server.external.stripe
  (:require
    [clj-http.client :as client]
    [clojure.spec :as s]
    [eponai.common.database :as db]
    [eponai.server.external.stripe.format :as f]
    [eponai.server.http :as h]
    [eponai.server.external.stripe.stub :as stub]
    [taoensso.timbre :refer [debug error info]]
    [clojure.data.json :as json]))

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

  (-create-customer [this opts])
  ;; Charges
  (-create-charge [this params])
  (-create-refund [this params]))


;(defn- set-api-key [api-key]
;  (if (some? api-key)
;    (set! (. Stripe apiKey) api-key)
;    (throw (ex-info "No Api key provided" {:message "No API key provided"
;                                           :cause   ::h/unprocessable-entity}))))

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

;; ############## Stripe record #################

(defn stripe-endpoint [path & [id]]
  (str "https://api.stripe.com/v1/"
       path
       (when id
         (str "/" id))))

(defrecord StripeRecord [api-key]
  IStripeConnect
  (-get-country-spec [_ code]
    (let [country-spec (json/read-str (:body (client/get (stripe-endpoint "country_specs" code) {:basic-auth api-key})) :key-fn keyword)]
      (debug "Fetched stripe country spec: " country-spec)
      (f/stripe->country-spec country-spec)))

  (-get-account [_ account-id]
    (let [account (json/read-str (:body (client/get (stripe-endpoint "accounts" account-id) {:basic-auth api-key})) :key-fn keyword)]
      (debug "Fetched stripe account: " account)
      (f/stripe->account account)))

  (-update-account [_ account-id params]
    (let [updated (json/read-str (:body (client/post (stripe-endpoint "accounts" account-id) {:basic-auth api-key :form-params params})) :key-fn keyword)]
      (debug "Updated: " updated)
      (f/stripe->account updated)))

  (-create-customer [_ {:keys [email]}]
    (let [params {:email email}
          customer (json/read-str (:body (client/post (stripe-endpoint "customers") {:basic-auth api-key :form-params params})) :key-fn keyword)]
      customer))

  (-create-account [_ {:keys [country]}]
    (let [params {:country country :managed true}
          account (json/read-str (:body (client/post (stripe-endpoint "accounts") {:basic-auth api-key :form-params params})) :key-fn keyword)
          keys (:keys account)]
      {:id     (:id account)
       :secret (:secret keys)
       :publ   (:publishable keys)}))

  (-create-charge [_ params]
    (let [charge (json/read-str (:body (client/post (stripe-endpoint "charges") {:basic-auth api-key :form-params params})) :key-fn keyword)]
      (debug "Created charge: " charge)
      {:charge/status (:status charge)
       :charge/id     (:id charge)
       :charge/paid?  (:paid charge)}))

  (-create-refund [_ {:keys [charge]}]
    (let [params {:charge           charge
                  :reverse_transfer true}
          refund (json/read-str (:body (client/post (stripe-endpoint "refunds") {:basic-auth api-key :form-params params})) :key-fn keyword)]
      (debug "Created refund: " refund)
      {:refund/status (:status refund)
       :refund/id     (:id refund)})))


;; ################## Public ##################

(defn stripe [api-key]
  (assert (some? api-key) "Stripe was not provided with an API key, make sure a key is set in you environment.")
  (->StripeRecord api-key))


(defn stripe-stub [api-key]
  (assert (some? api-key) "DEV - Fake Stripe: Stripe was not provided with an API key, make sure a string key is set in you environment. The provided key will not be used, but is required to replicate real behavior.")
  (let [state (atom {:accounts  {"acct_19k3ozC0YaFL9qxh" (stub/default-account "acct_19k3ozC0YaFL9qxh")}
                     :charges   {}
                     :customers {}})]
    (reify IStripeConnect
      (-get-country-spec [_ code]
        (let [specs (get stub/country-specs code)]
          (f/stripe->country-spec specs)))

      (-create-account [_ {:keys [country]}]
        (let [{:keys [accounts]} @state
              id (count accounts)
              new-account {:id      id
                           :secret  (str "secret_" id)
                           :publ    (str "publ_" id)
                           :country country
                           :managed true}]
          (swap! state update :accounts assoc id new-account)
          new-account))

      (-get-account [_ account-id]
        (let [{:keys [accounts]} @state
              account (get accounts account-id)]
          (debug "Fetched stub account: " account)
          (f/stripe->account account)))

      (-update-account [_ account-id params]
        (debug "Update account wiht params: " params)
        (let [{:keys [accounts]} @state
              account (get accounts account-id)
              new-account (cond-> (merge account (dissoc params :external_account :payout_schedule))
                                  (some? (:external_account params))
                                  (assoc :external_accounts {:data [{:id                   account-id
                                                                     :currency             "CAD"
                                                                     :default_for_currency true
                                                                     :bank_name            "Wells Fargo"
                                                                     :last4                "1234"
                                                                     :country              "CA"}]})
                                  (some? (:payout_schedule params))
                                  (assoc :transfer_schedule (:payout_schedule params)))
              stripe-account (stub/add-account-verifications new-account)]
          (swap! state update :accounts assoc account-id stripe-account)
          (f/stripe->account stripe-account)))

      (-create-customer [_ {:keys [email]}]
        (let [{:keys [customers]} @state
              id (count customers)
              new-customer {:id    (str id)
                            :email email}]
          (swap! state update :customers assoc id new-customer)
          new-customer))

      ;; Charge
      (-create-charge [_ params]
        (let [{:keys [charges]} @state
              id (str (count charges))
              charge (assoc params :id id :status "succeeded" :paid true)]
          (swap! state update :charges assoc id charge)
          {:charge/status (:status charge)
           :charge/id     (:id charge)
           :charge/paid?  (:paid charge)}))

      (-create-refund [this params]))))
