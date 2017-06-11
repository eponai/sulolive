(ns eponai.common.ui.store.account.validate
  (:require
    #?(:cljs
       [cljs.spec :as s]
       :clj
        [clojure.spec :as s])
        [taoensso.timbre :refer [debug]]))

(def stripe-verifications
  {:field.legal-entity.address/line1      "legal_entity.address.line1"
   :field.legal-entity.address/postal     "legal_entity.address.postal_code"
   :field.legal-entity.address/city       "legal_entity.address.city"
   :field.legal-entity.address/state      "legal_entity.address.state"

   :field.legal-entity/type               "legal_entity.type"
   :field.legal-entity/business-name      "legal_entity.business_name"
   :field.legal-entity/business-tax-id    "legal_entity.business_tax_id"

   :field.legal-entity.dob/day            "legal_entity.dob.day"
   :field.legal-entity.dob/month          "legal_entity.dob.month"
   :field.legal-entity.dob/year           "legal_entity.dob.year"

   :field.legal-entity/first-name         "legal_entity.first_name"
   :field.legal-entity/last-name          "legal_entity.last_name"
   :field.legal-entity/personal-id-number "legal_entity.personal_id_number"

   :field/external-account                "external_account"})

(def form-inputs
  (-> stripe-verifications
      (dissoc :field/external-account)
      (merge {:field.external-account/currency           "external_account.currency"
              :field.external-account/country            "external_account.country"
              :field.external-account/transit-number     "external_account.transit_number"
              :field.external-account/institution-number "external_account.institution_number"
              :field.external-account/account-number     "external_account.account_number"

              :field.general/store-name                  "general.store-name"
              :field.general/store-tagline               "general.store-tagline"})))

(defn validate
  [spec m & [prefix]]
  (when-let [err (s/explain-data spec m)]
    (let [problems (::s/problems err)
          invalid-paths (map (fn [p]
                               (str prefix (some #(get form-inputs %) p)))
                             (map :path problems))]
      {:explain-data  err
       :invalid-paths invalid-paths})))


;; ############## Specs #########################


