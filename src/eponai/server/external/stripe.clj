(ns eponai.server.external.stripe
  (:require
    [clj-http.client :as client]
    [slingshot.slingshot :refer [try+]]
    [com.stuartsierra.component :as component]
    [eponai.common.database :as db]
    [eponai.server.external.stripe.format :as f]
    [eponai.server.external.stripe.stub :as stub]
    [taoensso.timbre :refer [debug error info]]
    [clojure.data.json :as json]
    [clojure.string :as string]
    [eponai.common.format.date :as date])
  (:import (java.io File)
           (org.apache.commons.io FileUtils)))

(defn pull-stripe [db store-id]
  (when store-id
    (db/pull-one-with db '[*] {:where   '[[?s :store/stripe ?e]]
                               :symbols {'?s store-id}})))

(defn pull-user-stripe [db user-id]
  (when user-id
    (db/pull-one-with db '[*] {:where   '[[?u :user/stripe ?e]]
                               :symbols {'?u user-id}})))
;; ########## Stripe protocol ################

(defprotocol IStripeEndpoint
  (-post [this params path opts])
  (-get [this path opts])
  (-delete [this path opts])
  (-upload [this params path opts]))

(defprotocol IStripeConnect

  (-get-country-spec [this code])

  (-create-account [this opts]
    "Create a managed account on Stripe for a a seller.
    Opts is a map with following keys:
    :country - A two character string code for the country of the seller, e.g. 'US'.")
  (-delete-account [this account-id])

  (-get-account [this account-id]
    "Get a managed account for a seller from Stripe.
    Opts is a map with following keys:
    :country - A two character string code for the country of the seller, e.g. 'US'.")

  (-update-account [this account-id params])

  (-file-upload [this params]
    "Uploads a file using the FileUpload api: https://stripe.com/docs/file-upload")

  ;; Connected account
  (-get-balance [this account-id secret])
  (-get-payouts [this account-id])

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

(defn delete-account [stripe account-id]
  (-delete-account stripe account-id))

(defn update-account [stripe account-id params]
  (let [account (f/input->account-params params)]
    (debug "Update account params: " params)
    (debug "Update account: " account)
    ;(s/assert :ext.stripe.params/update-account account)
    (-update-account stripe account-id account)))

(defn upload-identity-document [stripe account-id {:keys [file file-type]}]
  (let [{:keys [id]} (-file-upload stripe {:file      file
                                           :file-type file-type
                                           :purpose   "identity_document"})
        account (f/input->account-params {:field/legal-entity
                                          {:field.legal-entity/verification
                                           {:field.legal-entity.verification/document id}}})]
    (debug "Updating account: " account)
    (-update-account stripe account-id account)))

(defn get-balance [stripe account-id secret]
  (if (not-empty account-id)
    (-get-balance stripe account-id secret)
    (throw (ex-info "Cannot fetch balance for nil account" {}))))

(defn get-payouts [stripe account-id]
  (if (not-empty account-id)
    (-get-payouts stripe account-id)
    (throw (ex-info "Cannot fetch balance for nil account" {}))))

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

(def stripe-api-version "2017-06-05")
(def stripe-api-host "https://api.stripe.com/v1")
(def stripe-upload-api-host "https://uploads.stripe.com/v1")

(defrecord StripeRecord [api-key]
  component/Lifecycle
  (start [this]
    ;; Do the check for the API key once we're trying to start the system
    (assert (some? api-key) "Stripe was not provided with an API key, make sure a key is set in you environment.")
    this)
  (stop [this]
    this)

  IStripeEndpoint
  (-post [_ params path opts]
    (let [url (string/join "/" (into [stripe-api-host] (remove nil? path)))]
      (json/read-str (:body (client/post url {:form-params params
                                              :basic-auth  api-key
                                              :headers     {"Stripe-Version" stripe-api-version}})) :key-fn keyword)))
  (-upload [_ multipart-params path opts]
    (let [url (string/join "/" (into [stripe-upload-api-host] (remove nil? path)))]
      (json/read-str (:body (client/post url {:multipart  multipart-params
                                              :basic-auth api-key
                                              :headers    {"Stripe-Version" stripe-api-version}})) :key-fn keyword)))
  (-get [_ path {:keys [connected-account]}]
    (debug "STRIPE - get for account: " connected-account)
    (let [url (string/join "/" (into [stripe-api-host] (remove nil? path)))
          headers (cond-> {"Stripe-Version" stripe-api-version}
                          (some? connected-account)
                          (assoc "Stripe-Account" connected-account))]
      (json/read-str (:body (client/get url {:basic-auth api-key
                                             :headers    headers})) :key-fn keyword)))

  (-delete [_ path opts]
    (let [url (string/join "/" (into [stripe-api-host] (remove nil? path)))]
      (json/read-str (:body (client/delete url {:basic-auth api-key
                                                :headers    {"Stripe-Version" stripe-api-version}})) :key-fn keyword)))

  IStripeConnect
  (-get-country-spec [this code]
    (let [country-spec (-get this ["country_specs" code] nil)]
      (debug "STRIPE - fetched country spec: " country-spec)
      (f/stripe->country-spec country-spec)))

  (-create-account [this {:keys [country]}]
    (let [params {:country country :type "custom"}
          account (-post this params ["accounts"] nil)
          keys (:keys account)]
      (debug "STRIPE - created account: " account)
      {:id     (:id account)
       :secret (:secret keys)
       :publ   (:publishable keys)}))

  (-delete-account [this account-id]
    (let [deleted (-delete this ["accounts" account-id] nil)]
      deleted))

  (-get-account [this account-id]
    (let [account (-get this ["accounts" account-id] nil)]
      (debug "STRIPE - fetched account: " account)
      (f/stripe->account account)))

  (-update-account [this account-id params]
    (try+
      (let [updated (-post this params ["accounts" account-id] nil)]
        (debug "STRIPE - updated account: " updated)
        (f/stripe->account updated))
      (catch [:status 400] r
        (let [{:keys [body]} r
              {:keys [error]} (json/read-str body :key-fn keyword)]
          (throw (ex-info (:message error) error))))))

  (-file-upload [this {:keys [purpose file file-type]}]
    (try+
      (let [uploaded (-upload this [{:name "file" :content file :mime-type file-type}
                                    {:name "purpose" :content purpose}]
                              ["files"] nil)]
        (debug "Uploaded file: " file " response: " uploaded)
        uploaded)
      (catch [:status 400] r
        (let [{:keys [body]} r
              {:keys [error]} (json/read-str body :key-fn keyword)]
          (debug "Error uploading file: " file " error: " (:message error))
          (throw (ex-info (:message error) error))))))

  (-get-balance [this account-id secret]
    (let [balance (-get this ["balance"] {:connected-account account-id})]
      (debug "STRIPE - fetch balance: " balance)
      (f/stripe->balance balance account-id)))

  (-get-payouts [this account-id]
    (let [payouts (-get this ["payouts"] {:connected-account account-id})]
      (debug "Payouts: " payouts)
      (f/stripe->payouts payouts account-id)))

  (-create-customer [this params]
    (let [customer (-post this params ["customers"] nil)]
      (debug "STRIPE - created new customer: " customer)
      (f/stripe->customer customer)))

  (-update-customer [this customer-id params]
    (let [customer (-post this params ["customers" customer-id] nil)]
      (debug "STRIPE - updated customer: " customer)
      (f/stripe->customer customer)))

  (-get-customer [this customer-id]
    (let [customer (-get this ["customers" customer-id] nil)]
      (debug "STRIPE - fetched customer: " customer)
      (f/stripe->customer customer)))

  (-create-card [this customer-id source]
    (try+
      (let [params {:source source}
            card (-post this params ["customers" customer-id "sources"] nil)]
        (debug "STRIPE - created card: " card)
        card)
      (catch [:status 402] r
        (let [{:keys [body]} r
              {:keys [error]} (json/read-str body :key-fn keyword)]
          ;(debug "Error response: " (clojure.walk/keywordize-keys body))
          (throw (ex-info (:message error) error))))))

  (-delete-card [this customer-id card-id]
    ;https://api.stripe.com/v1/customers/{CUSTOMER_ID}/sources/{CARD_ID}
    (let [deleted (-delete this ["customers" customer-id "sources" card-id] nil)]
      (debug "STRIPE - deleted card: " deleted)
      deleted))

  (-create-charge [this params]
    (try+
      (let [charge (-post this params ["charges"] nil)]
        (debug "STRIPE - created charge: " charge)
        {:charge/status (:status charge)
         :charge/id     (:id charge)
         :charge/paid?  (:paid charge)})
      (catch [:status 402] r
        (let [{:keys [body]} r
              {:keys [error]} (json/read-str body :key-fn keyword)]
          (throw (ex-info (:message error) error))))))

  (-get-charge [this charge-id]
    (let [charge (-get this ["charges" charge-id] nil)]
      (debug "STRIPE - fetched charge: " charge)
      {:charge/status  (:status charge)
       :charge/id      (:id charge)
       :charge/source  (:source charge)
       :charge/created (:created charge)
       :charge/amount  (f/stripe->price (:amount charge))
       :charge/paid?   (:paid charge)
       :charge/amount-refunded (f/stripe->price (:amount_refunded charge))}))

  (-create-refund [this {:keys [charge]}]
    (let [params {:charge           charge
                  :reverse_transfer true}
          refund (-post this params ["refunds"] nil)]
      (debug "STRIPE - created refund: " refund)
      {:refund/status (:status refund)
       :refund/id     (:id refund)})))


;; ################## Public ##################

(defn stripe [api-key]
  (->StripeRecord api-key))


(defn stripe-stub [api-key]
  (let [state (atom {:accounts  {"acct_19k3ozC0YaFL9qxh" (stub/default-account "acct_19k3ozC0YaFL9qxh")}
                     :charges   {}
                     :customers {"0" {:id      "0"
                                      :sources {:data [{:brand "Visa", :last4 1234, :exp_year 2018, :exp_month 4}
                                                       {:brand "MasterCard" :last4 1234 :exp_year 2018 :exp_month 4}
                                                       {:brand "Random" :last4 1234 :exp_year 2018 :exp_month 4}]}}}})
        temp-dir (File/createTempFile "stripe-stub" "temp-dir")]
    (.delete temp-dir)
    (.mkdir temp-dir)
    (.deleteOnExit temp-dir)
    (reify
      component/Lifecycle
      (start [this]
        ;; Do the check for the API key once we're trying to start the system
        (assert (some? api-key) "DEV - Fake Stripe: Stripe was not provided with an API key, make sure a string key is set in you environment. The provided key will not be used, but is required to replicate real behavior.")
        this)
      (stop [this]
        this)

      IStripeConnect
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
                                         :amount   123434}]
                            :pending   [{:currency "cad"
                                         :amount   1234}]}
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

      (-file-upload [_ {:keys [purpose file file-type]}]
        (let [id (str "fake_upload_file_" (rand-int 100000000))
              to-file (File. temp-dir id)
              _ (.createNewFile to-file)
              _ (.deleteOnExit to-file)
              _ (FileUtils/copyFile ^File file to-file)
              ret {:id      id,
                   :object  "file_upload",
                   :created (.lastModified to-file)
                   :purpose purpose
                   :size    (.length to-file)
                   :type    (cond-> file-type
                                    (string/starts-with? file-type "image/")
                                    (string/replace-first "image/" ""))
                   :url     nil}]
          (swap! state assoc-in [:file-uploads id] ret)
          ret))

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
