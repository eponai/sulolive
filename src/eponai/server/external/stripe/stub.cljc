(ns eponai.server.external.stripe.stub)

(def country-specs
  {"CA" {:id                                "CA"
         :default_currency                  "cad"
         :supported_bank_account_currencies {"cad" ["CA"]
                                             "usd" ["US" "CA"]}
         :supported_payment_currencies      ["cad" "usd"]
         :verification_fields               {:individual {:minimum    #{"external_account",
                                                                        "legal_entity.address.city",
                                                                        "legal_entity.address.line1",
                                                                        "legal_entity.address.postal_code",
                                                                        "legal_entity.address.state",
                                                                        "legal_entity.dob.day",
                                                                        "legal_entity.dob.month",
                                                                        "legal_entity.dob.year",
                                                                        "legal_entity.first_name",
                                                                        "legal_entity.last_name",
                                                                        "legal_entity.personal_id_number",
                                                                        "legal_entity.type",
                                                                        "tos_acceptance.date",
                                                                        "tos_acceptance.ip"}
                                                          :additional #{"legal_entity.verification.document"}}
                                             :company    {:minimum    #{"external_account",
                                                                        "legal_entity.address.city",
                                                                        "legal_entity.address.line1",
                                                                        "legal_entity.address.postal_code",
                                                                        "legal_entity.address.state",
                                                                        "legal_entity.business_name",
                                                                        "legal_entity.business_tax_id",
                                                                        "legal_entity.dob.day",
                                                                        "legal_entity.dob.month",
                                                                        "legal_entity.dob.year",
                                                                        "legal_entity.first_name",
                                                                        "legal_entity.last_name",
                                                                        "legal_entity.personal_id_number",
                                                                        "legal_entity.type",
                                                                        "tos_acceptance.date",
                                                                        "tos_acceptance.ip"}
                                                          :additional #{"legal_entity.verification.document"}}}}})

(defn default-account [id]
  {:id      id
   :country "ca"
   :payout_schedule {:delay_days 7
                     :interval "daily"}})

(defn add-account-verifications [account]
  (let [{:keys [external_accounts legal_entity]} account
        {:keys [first_name last_name dob address type]} legal_entity
        fields-needed (cond-> ["tos"]
                              (empty? external_accounts)
                              (conj "external_account")
                              (nil? first_name)
                              (conj "legal_entity.first_name")
                              (nil? last_name)
                              (conj "legal_entity.last_name")
                              (nil? dob)
                              (conj "legal_entity.dob.day" "legal_entity.dob.month" "legal_entity.dob.year")
                              (nil? type)
                              (conj "legal_entity.type")
                              (nil? address)
                              (conj "legal_entity.address.city" "legal_entity.address.line1" "legal_entity.address.postal_code" "legal_entity.address.state"))]
    (assoc account :verification {:fields_needed fields-needed}
                   :details_submitted (some? legal_entity))))