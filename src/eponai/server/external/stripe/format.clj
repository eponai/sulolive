(ns eponai.server.external.stripe.format
  (:require
    [eponai.common.format :as f])
  (:import
    (com.stripe.model Address
                      Account
                      Account$Verification
                      AccountTransferSchedule
                      BankAccount
                      ExternalAccount
                      Card
                      CountrySpec
                      LegalEntity
                      LegalEntity$DateOfBirth
                      LegalEntity$Verification
                      VerificationFields
                      VerificationFieldsDetails)))

(defn stripe->verification-fields [^VerificationFields v]
  (let [min-fields (fn [^VerificationFieldsDetails field-details]
                     (.getMinimum field-details))]
    {:country-spec.verification-fields/individual {:country-spec.verification-fields.individual/minimum (min-fields (.getIndividual v))}
     :country-spec.verification-fields/company    {:country-spec.verification-fields.company/minimum (min-fields (.getCompany v))}}))

(defn stripe->country-spec [^CountrySpec c]
  {:country-spec/id                                (.getId c)
   :country-spec/default-currency                  (.getDefaultCurrency c)
   :country-spec/supported-payment-currencies      (.getSupportedPaymentCurrencies c)
   :country-spec/supported-bank-account-currencies (.getSupportedBankAccountCurrencies c)
   :country-spec/supported-payment-methods         (.getSupportedPaymentMethods c)
   :country-spec/verification-fields               (stripe->verification-fields (.getVerificationFields c))})

(defn stripe->verification [^Account$Verification v]
  (f/remove-nil-keys
    {:stripe.verification/fields-needed (.getFieldsNeeded v)
     :stripe.verification/due-by        (.getDueBy v)
     :stripe.verification/disabled-reason (.getDisabledReason v)}))


(defn stripe->legal-entity [^LegalEntity le]
  (let [dob* (fn [^LegalEntity$DateOfBirth dob]
               (f/remove-nil-keys
                 {:stripe.legal-entity.dob/year  (.getYear dob)
                  :stripe.legal-entity.dob/month (.getMonth dob)
                  :stripe.legal-entity.dob/day   (.getDay dob)}))
        address* (fn [^Address a]
                   {:stripe.legal-entity.address/city   (.getCity a)
                    :stripe.legal-entity.address/postal (.getPostalCode a)
                    :stripe.legal-entity.address/line1  (.getLine1 a)
                    :stripe.legal-entity.address/state  (.getState a)})
        verification* (fn [^LegalEntity$Verification v]
                        {:stripe.legal-entity.verification/status (.getStatus v)
                         :stripe.legal-entity.verification/details (.getDetails v)})]
    (f/remove-nil-keys
      {:stripe.legal-entity/first-name    (.getFirstName le)
       :stripe.legal-entity/last-name     (.getLastName le)
       :stripe.legal-entity/dob           (dob* (.getDob le))
       :stripe.legal-entity/address       (address* (.getAddress le))
       :stripe.legal-entity/business-name (.getBusinessName le)
       :stripe.legal-entity/type          (keyword (.getType le))
       :stripe.legal-entity/verification  (verification* (.getVerification le))})))

(defn stripe->bank-account [^BankAccount ba]
  {:stripe.external-account/id                    (.getId ba)
   :stripe.external-account/bank-name             (.getBankName ba)
   :stripe.external-account/routing-number        (.getRoutingNumber ba)
   :stripe.external-account/default-for-currency? (.getDefaultForCurrency ba)
   :stripe.external-account/status                (.getStatus ba)
   :stripe.external-account/last4                 (.getLast4 ba)
   :stripe.external-account/currency              (.getCurrency ba)
   :stripe.external-account/country               (.getCountry ba)})

(defn stripe->card [^Card c]
  {:stripe.external-account/id    (.getId c)
   :stripe.external-account/last4 (.getLast4 c)
   :stripe.external-account/brand (.getBrand c)})

(defn stripe->account [^Account a]
  (let [ext-account* (fn [^ExternalAccount ea]
                       (let [object (.getObject ea)
                             account (cond (= "bank_account")
                                           (stripe->bank-account ea)
                                           (= "card")
                                           (stripe->card ea))]
                         (assoc account :stripe.external-account/object object)))
        payout-schedule* (fn [^AccountTransferSchedule ats]
                           {:stripe.payout-schedule/interval     (.getInterval ats)
                            :stripe.payout-schedule/month-anchor (.getMonthlyAnchor ats)
                            :stripe.payout-schedule/week-anchor  (.getWeeklyAnchor ats)
                            :stripe.payout-schedule/delay-days (.getDelayDays ats)})]
    (f/remove-nil-keys
      {:stripe/id                 (.getId a)
       :stripe/business-name      (.getBusinessName a)
       :stripe/business-url       (.getBusinessURL a)
       :stripe/charges-enabled?   (.getChargesEnabled a)
       :stripe/country            (.getCountry a)
       :stripe/default-currency   (.getDefaultCurrency a)
       :stripe/details-submitted? (.getDetailsSubmitted a)
       :stripe/external-accounts  (map ext-account* (.getData (.getExternalAccounts a)))
       :stripe/legal-entity       (stripe->legal-entity (.getLegalEntity a))
       :stripe/payouts-enabled?   (.getTransfersEnabled a)
       :stripe/payout-schedule    (payout-schedule* (.getTransferSchedule a))
       :stripe/verification       (stripe->verification (.getVerification a))})))

(defn input->legal-entity [legal-entity]
  (let [{:field.legal-entity/keys [type address business-name business-tax-id first-name last-name dob personal-id-number]} legal-entity
        {:field.legal-entity.address/keys [line1 postal city state]} address
        {:field.legal-entity.dob/keys [day month year]} dob
        ]
    (f/remove-nil-keys
      {:first_name         first-name
       :last_name          last-name
       :dob                (f/remove-nil-keys
                             {:day   day
                              :month month
                              :year  year})
       :address            (f/remove-nil-keys
                             {:city        city
                              :line1       line1
                              :postal_code postal
                              :state       state})
       :type               (when (some? type) (name type))
       :personal_id_number personal-id-number})))

(defn input->payout-schedule [ps]
  (let [{:field.payout-schedule/keys [interval month-anchor week-anchor]} ps]
    (f/remove-nil-keys
      {:interval interval
       :monthly_anchor month-anchor
       :weekly_anchor week-anchor})))

(defn input->account-params [account-params]
  (let [{:field/keys [legal-entity external-account tos-acceptance default-currency payout-schedule]} account-params]
    (f/remove-nil-keys
      {:legal_entity     (input->legal-entity legal-entity)
       :external_account external-account
       :tos_acceptance   (f/remove-nil-keys
                           {:date (:field.tos-acceptance/date tos-acceptance)
                            :ip   (:field.tos-acceptance/ip tos-acceptance)})
       :default_currency default-currency
       :payout_schedule  (input->payout-schedule payout-schedule)})))