(ns eponai.server.external.stripe
  (:require
    [clj-http.client :as client]
    [slingshot.slingshot :refer [try+]]
    [clojure.spec :as s]
    [eponai.common.database :as db]
    [eponai.server.external.stripe.format :as f]
    [eponai.server.http :as h]
    [eponai.server.external.stripe.stub :as stub]
    [taoensso.timbre :refer [debug error info]]
    [eponai.server.external.stripe.webhooks :as webhooks]
    [clojure.data.json :as json]
    [clojure.string :as string]
    [eponai.common.format.date :as date])
  (:import (clojure.lang ExceptionInfo)))

(defn pull-stripe [db store-id]
  (when store-id
    (db/pull-one-with db '[*] {:where   '[[?s :store/stripe ?e]]
                               :symbols {'?s store-id}})))

(defn pull-user-stripe [db user-id]
  (when user-id
    (db/pull-one-with db '[*] {:where   '[[?u :user/stripe ?e]]
                               :symbols {'?u user-id}})))
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

  (-get-balance [this account-id secret])

  ;; Customers
  (-create-customer [this opts])
  (-update-customer [this customer-id opts])
  (-get-customer [this customer-id])

  (-create-card [this customer-id source])
  (-delete-card [this customer-id card-id])

  ;; Charges
  (-create-charge [this params])
  (-get-charge [this charge-id])
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
    ;(s/assert :ext.stripe.params/update-account account)
    (-update-account stripe account-id account)))

(defn get-balance [stripe account-id secret]
  (-get-balance stripe account-id secret))

(defn create-charge [stripe params]
  (debug "Create charge: " params)
  (-create-charge stripe params))

(defn get-charge [stripe charge-id]
  (-get-charge stripe charge-id))

(defn create-refund [stripe params]
  (-create-refund stripe params))

(defn create-customer [stripe params]
  (-create-customer stripe params))

(defn update-customer [stripe customer-id params]
  (-update-customer stripe customer-id params))

(defn create-card [stripe customer-id source]
  (-create-card stripe customer-id source))

(defn delete-card [stripe customer-id card-id]
  (-delete-card stripe customer-id card-id))

(defn get-customer [stripe customer-id]
  (-get-customer stripe customer-id))

;; ############## Stripe record #################

(defn stripe-endpoint [& path]
  (string/join "/" (into ["https://api.stripe.com/v1"] (remove nil? path))))

(defrecord StripeRecord [api-key]
  IStripeConnect
  (-get-country-spec [_ code]
    (let [country-spec (json/read-str (:body (client/get (stripe-endpoint "country_specs" code) {:basic-auth api-key})) :key-fn keyword)]
      (debug "STRIPE - fetched country spec: " country-spec)
      (f/stripe->country-spec country-spec)))

  (-get-account [_ account-id]
    (let [account (json/read-str (:body (client/get (stripe-endpoint "accounts" account-id) {:basic-auth api-key})) :key-fn keyword)]
      (debug "STRIPE - fetched account: " account)
      (f/stripe->account account)))

  (-update-account [_ account-id params]
    (try+
      (let [updated (json/read-str (:body (client/post (stripe-endpoint "accounts" account-id) {:basic-auth api-key :form-params params})) :key-fn keyword)]
        (debug "STRIPE - updated account: " updated)
        (f/stripe->account updated))
      (catch [:status 400] r
        (let [{:keys [body]} r
              {:keys [error]} (json/read-str body :key-fn keyword)]
          ;(debug "Error response: " (clojure.walk/keywordize-keys body))
          (throw (ex-info (:message error) error))))))

  (-get-balance [_ account-id secret]
    (let [balance (json/read-str (:body (client/get (stripe-endpoint "balance") {:basic-auth secret})) :key-fn keyword)]
      (debug "STRIPE - fetch balance: " balance)
      (f/stripe->balance balance account-id)))

  (-create-customer [_ params]
    (let [customer (json/read-str (:body (client/post (stripe-endpoint "customers") {:basic-auth api-key :form-params params})) :key-fn keyword)]
      (debug "STRIPE - created new customer: " customer)
      (f/stripe->customer customer)))

  (-update-customer [_ customer-id params]
    (let [customer (json/read-str (:body (client/post (stripe-endpoint "customers" customer-id)
                                                      {:basic-auth api-key :form-params params :throw-exceptions false})) :key-fn keyword)]
      (debug "STRIPE - updated customer: " customer)
      (f/stripe->customer customer)))

  (-get-customer [_ customer-id]
    (let [customer (json/read-str (:body (client/get (stripe-endpoint "customers" customer-id) {:basic-auth api-key})) :key-fn keyword)]
      (debug "STRIPE - fetched customer: " customer)
      (f/stripe->customer customer)))

  (-create-card [_ customer-id source]
    (try+
      (let [params {:source source}
            card (json/read-str (:body (client/post (stripe-endpoint "customers" customer-id "sources")
                                                    {:basic-auth api-key :form-params params})) :key-fn keyword)]
        (debug "STRIPE - created card: " card)
        card)
      (catch [:status 402] r
        (let [{:keys [body]} r
              {:keys [error]} (json/read-str body :key-fn keyword)]
          ;(debug "Error response: " (clojure.walk/keywordize-keys body))
          (throw (ex-info (:message error) error))))))

  (-delete-card [_ customer-id card-id]
    ;https://api.stripe.com/v1/customers/{CUSTOMER_ID}/sources/{CARD_ID}
    (let [deleted (json/read-str (:body (client/delete (stripe-endpoint "customers" customer-id "sources" card-id)
                                                       {:basic-auth api-key})) :key-fn keyword)]
      (debug "STRIPE - deleted card: " deleted)
      deleted))

  (-create-account [_ {:keys [country]}]
    (let [params {:country country :managed true}
          account (json/read-str (:body (client/post (stripe-endpoint "accounts") {:basic-auth api-key :form-params params})) :key-fn keyword)
          keys (:keys account)]
      (debug "STRIPE - created account: " account)
      {:id     (:id account)
       :secret (:secret keys)
       :publ   (:publishable keys)}))

  (-create-charge [_ params]
    (try+
      (let [charge (json/read-str (:body (client/post (stripe-endpoint "charges") {:basic-auth  api-key
                                                                                   :form-params params})) :key-fn keyword)]
        (debug "STRIPE - created charge: " charge)
        {:charge/status (:status charge)
         :charge/id     (:id charge)
         :charge/paid?  (:paid charge)})
      (catch [:status 402] r
        (let [{:keys [body]} r
              {:keys [error]} (json/read-str body :key-fn keyword)]
          (throw (ex-info (:message error) error))))))

  (-get-charge [_ charge-id]
    (let [charge (json/read-str (:body (client/get (stripe-endpoint "charges" charge-id) {:basic-auth api-key})) :key-fn keyword)]
      (debug "STRIPE - fetched charge: " charge)
      {:charge/status  (:status charge)
       :charge/id      (:id charge)
       :charge/source  (:source charge)
       :charge/created (:created charge)
       :charge/amount  (f/stripe->price (:amount charge))
       :charge/paid?   (:paid charge)}))

  (-create-refund [_ {:keys [charge]}]
    (let [params {:charge           charge
                  :reverse_transfer true}
          refund (json/read-str (:body (client/post (stripe-endpoint "refunds") {:basic-auth api-key :form-params params})) :key-fn keyword)]
      (debug "STRIPE - created refund: " refund)
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
                     :customers {"0" {:id      "0"
                                      :sources {:data [{:brand "Visa", :last4 1234, :exp_year 2018, :exp_month 4}
                                                       {:brand "MasterCard" :last4 1234 :exp_year 2018 :exp_month 4}
                                                       {:brand "Random" :last4 1234 :exp_year 2018 :exp_month 4}]}}}})]
    (reify IStripeConnect
      (-get-country-spec [_ code]
        (let [specs (get stub/country-specs code)]
          (f/stripe->country-spec specs)))

      (-create-account [_ {:keys [country]}]
        (let [{:keys [accounts]} @state
              id (str (count accounts))
              new-account {:id      id
                           :secret  (str "secret_" id)
                           :publ    (str "publ_" id)
                           :country country
                           :managed true}]
          (debug "Created fake Stripe account: " new-account)
          (swap! state update :accounts assoc id new-account)
          new-account))

      (-get-account [_ account-id]
        (let [{:keys [accounts]} @state
              account (get accounts account-id)]
          (debug "Fetched fake Stripe account: " account)
          (f/stripe->account account)))
      (-get-balance [_ account-id secret]
        (f/stripe->balance {:available [{:currency "cad"
                                         :amount 123434}]
                            :pending [{:currency "cad"
                                       :amount 1234}]}
                           account-id))

      (-update-account [_ account-id params]
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
                                  :always
                                  (update :legal_entity merge (:legal_entity account)))
              stripe-account (stub/add-account-verifications new-account)]
          (debug "Updated fake Stripe account: " stripe-account)
          (swap! state update :accounts assoc account-id stripe-account)
          (f/stripe->account stripe-account)))

      (-create-customer [_ params]
        (let [{:keys [customers]} @state
              id (str (count customers))
              new-customer (-> params
                               (assoc :id id)
                               (assoc :sources {:data [{:id "0" :brand "Visa" :last4 1234 :exp_year 2018 :exp_month 4}]})
                               (assoc :default_source "0"))]
          (debug "Created fake Stripe customer: " new-customer)
          (swap! state update :customers assoc id new-customer)
          (debug "New state: " @state)
          (f/stripe->customer new-customer)))

      (-update-customer [_ customer-id params]
        (let [{:keys [customers]} @state
              customer (get customers customer-id)]
          (if (some? customer)
            (let [new-customer (update customer :sources conj {:brand "Visa" :last4 1234 :exp_year 2018 :exp_month 4})]
              (debug "Updated fake Stripe customer: " new-customer)
              (swap! state update :customers assoc customer-id new-customer)
              (f/stripe->customer new-customer))
            (throw (ex-info (str "Customer with id: " customer-id " does not exist.")
                            {:id     customer-id
                             :params params})))))

      (-get-customer [_ customer-id]
        (debug "Get customer id: " customer-id)
        (debug "Get customer " @state)
        (let [{:keys [customers]} @state
              customer (get customers customer-id)]
          (f/stripe->customer customer)))

      ;; Charge
      (-create-charge [_ params]
        (let [{:keys [charges]} @state
              id (str (count charges))
              charge (assoc params :id id :status "succeeded" :paid true)]
          (debug "Created fake Stripe charge: " charge)
          (swap! state update :charges assoc id charge)
          {:charge/status (:status charge)
           :charge/id     (:id charge)
           :charge/paid?  (:paid charge)}))

      (-get-charge [_ charge-id]
        {:charge/amount  100
         :charge/created (date/current-secs)})

      (-create-card [_ customer-id card]
        )
      (-create-refund [this params]))))

(defn webhook [{:keys [type] :as env} event]
  (cond (= type :account)
        (or (webhooks/handle-account-webhook env event) {})

        (= type :connected)
        (or (webhooks/handle-connected-webhook env event) {})))
