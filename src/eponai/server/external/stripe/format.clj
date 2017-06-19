(ns eponai.server.external.stripe.format
  (:require
    [eponai.common.format :as f]))

(defn stripe->verification-fields [verification-fields]
  {:country-spec.verification-fields/individual {:country-spec.verification-fields.individual/minimum (:minimum (:individual verification-fields))}
   :country-spec.verification-fields/company    {:country-spec.verification-fields.company/minimum (:minimum (:company verification-fields))}})

(defn stripe->country-spec [country-spec]
  (f/remove-nil-keys
    {:country-spec/id                                (:id country-spec)
     :country-spec/default-currency                  (:default_currency country-spec)
     :country-spec/supported-payment-currencies      (:supported_payment_currencies country-spec)
     :country-spec/supported-bank-account-currencies (:supported_bank_account_currencies country-spec)
     :country-spec/supported-payment-methods         (:supported_payment_methods country-spec)
     :country-spec/verification-fields               (stripe->verification-fields (:verification_fields country-spec))}))

(defn stripe->verification [verification]
  (let [fields ["legal_entity.address.line1" "legal_entity.address.postal_code" "legal_entity.address.city"
                "legal_entity.address.state"
                "legal_entity.type"
                "legal_entity.business_name"
                "legal_entity.business_tax_id"
                "legal_entity.dob.day"
                "legal_entity.dob.month"
                "legal_entity.dob.year"
                "legal_entity.first_name"
                "legal_entity.last_name"
                "legal_entity.personal_id_number"
                "external_account"
                "tos_acceptance.date"
                "tos_acceptance.ip"]]
    (f/remove-nil-keys
      {:stripe.verification/fields-needed   (:fields_needed verification)
       :stripe.verification/due-by          (:due_by verification)
       :stripe.verification/disabled-reason (:disabled_reason verification)})))


(defn stripe->legal-entity [legal-entity]
  (let [dob* (fn [dob]
               (f/remove-nil-keys
                 {:stripe.legal-entity.dob/year  (:year dob)
                  :stripe.legal-entity.dob/month (:month dob)
                  :stripe.legal-entity.dob/day   (:day dob)}))
        address* (fn [address]
                   {:stripe.legal-entity.address/city    (:city address)
                    :stripe.legal-entity.address/postal  (:postal_code address)
                    :stripe.legal-entity.address/line1   (:line1 address)
                    :stripe.legal-entity.address/state   (:state address)
                    :stripe.legal-entity.address/country (:country address)})
        verification* (fn [v]
                        {:stripe.legal-entity.verification/status  (:status v)
                         :stripe.legal-entity.verification/details (:details v)})]
    (f/remove-nil-keys
      {:stripe.legal-entity/first-name    (:first_name legal-entity)
       :stripe.legal-entity/last-name     (:last_name legal-entity)
       :stripe.legal-entity/dob           (dob* (:dob legal-entity))
       :stripe.legal-entity/address       (address* (:address legal-entity))
       :stripe.legal-entity/business-name (:business_name legal-entity)
       :stripe.legal-entity/type          (keyword (:type legal-entity))
       :stripe.legal-entity/verification  (verification* (:verification legal-entity))})))

(defn stripe->bank-account [bank-account]
  {:stripe.external-account/id                    (:id bank-account)
   :stripe.external-account/bank-name             (:bank_name bank-account)
   :stripe.external-account/routing-number        (:routing_number bank-account)
   :stripe.external-account/default-for-currency? (:default_for_currency bank-account)
   :stripe.external-account/status                (:status bank-account)
   :stripe.external-account/last4                 (:last4 bank-account)
   :stripe.external-account/currency              (:currency bank-account)
   :stripe.external-account/country               (:country bank-account)})

(defn stripe->card [card]
  {:stripe.external-account/id    (:id card)
   :stripe.external-account/last4 (:last4 card)
   :stripe.external-account/brand (:brand card)})

(defn stripe->account [account]
  (let [ext-account* (fn [ext-account]
                       (let [object (:object ext-account)
                             account (cond (= "bank_account")
                                           (stripe->bank-account ext-account)
                                           (= "card")
                                           (stripe->card ext-account))]
                         (assoc account :stripe.external-account/object object)))
        payout-schedule* (fn [payout-shedule]
                           (f/remove-nil-keys
                             {:stripe.payout-schedule/interval     (:interval payout-shedule)
                              :stripe.payout-schedule/month-anchor (:monthly_anchor payout-shedule)
                              :stripe.payout-schedule/week-anchor  (:weekly_anchor payout-shedule)
                              :stripe.payout-schedule/delay-days   (:delay_days payout-shedule)}))]
    (f/remove-nil-keys
      {:stripe/id                 (:id account)
       :stripe/business-name      (:business_name account)
       :stripe/business-url       (:business_url account)
       :stripe/charges-enabled?   (:charges_enabled account)
       :stripe/country            (:country account)
       :stripe/default-currency   (:default_currency account)
       :stripe/details-submitted? (:details_submitted account)
       :stripe/external-accounts  (map ext-account* (:data (:external_accounts account)))
       :stripe/legal-entity       (stripe->legal-entity (:legal_entity account))
       :stripe/payouts-enabled?   (:transfers_enabled account)
       :stripe/payout-schedule    (payout-schedule* (:payout_schedule account))
       :stripe/verification       (stripe->verification (:verification account))})))

(defn stripe->customer [customer]
  (let [source* (fn [s]
                  (f/remove-nil-keys
                    {:stripe.card/id        (:id s)
                     :stripe.card/brand     (:brand s)
                     :stripe.card/exp-month (:exp_month s)
                     :stripe.card/exp-year  (:exp_year s)
                     :stripe.card/last4     (:last4 s)
                     :stripe.card/name      (:name s)}))
        shipping* (fn [s]
                    (let [address* (fn [a]
                                     (f/remove-nil-keys
                                       {:shipping.address/street   (:line1 a)
                                        :shipping.address/street2  (:line2 a)
                                        :shipping.address/locality (:city a)
                                        :shipping.address/postal   (:postal_code a)
                                        :shipping.address/region   (:state a)
                                        :shipping.address/country  {:country/code (:country a)}}))]
                      (f/remove-nil-keys
                        {:shipping/name    (:name s)
                         :shipping/address (address* (:address s))})))]
    (f/remove-nil-keys
      {:stripe/id             (:id customer)
       :stripe/sources        (map source* (:data (:sources customer)))
       :stripe/default-source (:default_source customer)
       :stripe/shipping       (shipping* (:shipping customer))})))

(defn stripe->price [p]
  (bigdec (with-precision 10 (/ p 100))))

(defn stripe->balance [b id]
  (letfn [(balance* [a]
            {:stripe.balance/currency (:currency a)
             :stripe.balance/amount   (stripe->price (:amount a))})]
    (f/remove-nil-keys
      {:stripe/id      id
       :stripe/balance {:stripe.balance/available (map balance* (:available b))
                        :stripe.balance/pending   (map balance* (:pending b))}})))

(defn input->legal-entity [legal-entity]
  (let [{:field.legal-entity/keys [type address business-name business-tax-id first-name last-name dob personal-id-number]} legal-entity
        {:field.legal-entity.address/keys [line1 postal city state]} address
        {:field.legal-entity.dob/keys [day month year]} dob]
    (f/remove-nil-keys
      {:first_name         first-name
       :last_name          last-name
       :business_name      business-name
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
      {:interval       interval
       :monthly_anchor month-anchor
       :weekly_anchor  week-anchor})))

(defn input->account-params [account-params]
  (let [{:field/keys [legal-entity external-account tos-acceptance default-currency payout-schedule support-email]} account-params]
    (f/remove-nil-keys
      {:support_email    support-email
       :legal_entity     (input->legal-entity legal-entity)
       :external_account external-account
       :tos_acceptance   (f/remove-nil-keys
                           {:date (:field.tos-acceptance/date tos-acceptance)
                            :ip   (:field.tos-acceptance/ip tos-acceptance)})
       :default_currency default-currency
       :payout_schedule  (input->payout-schedule payout-schedule)})))