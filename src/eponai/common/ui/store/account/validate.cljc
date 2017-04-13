(ns eponai.common.ui.store.account.validate
  (:require
    #?(:cljs
       [cljs.spec :as s]
       :clj
        [clojure.spec :as s])
        [eponai.common :as c]
        [eponai.common.format :as f]
    #?(:cljs
       [eponai.web.utils :as utils])
        [taoensso.timbre :refer [debug]]))

(def stripe-verifications
  {:field.legal-entity.address/line1      "legal_entity.address.line1"
   :field.legal-entity.address/postal     "legal_entity.address.postal_code"
   :field.legal-entity.address/city       "legal_entity.address.city"
   :field.legal-entity.address/state      "legal_entity.address.state"

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

(s/def :field.legal-entity.address/line1 (s/and string? #(not-empty %)))
(s/def :field.legal-entity.address/postal (s/and string? #(not-empty %)))
(s/def :field.legal-entity.address/city (s/and string? #(not-empty %)))
(s/def :field.legal-entity.address/state (s/and string? #(not-empty %)))

(s/def :field.legal-entity.dob/day number?)
(s/def :field.legal-entity.dob/month number?)
(s/def :field.legal-entity.dob/year number?)

(s/def :field.legal-entity/address (s/keys :opt [:field.legal-entity.address/line1
                                               :field.legal-entity.address/postal
                                               :field.legal-entity.address/city
                                               :field.legal-entity.address/state]))

(s/def :field.legal-entity/dob (s/keys :req [:field.legal-entity.dob/day
                                           :field.legal-entity.dob/month
                                           :field.legal-entity.dob/year]))
(s/def :field.legal-entity/first-name (s/and string? #(not-empty %)))
(s/def :field.legal-entity/last-name (s/and string? #(not-empty %)))
(s/def :field.legal-entity/personal-id-number (s/and string? #(not-empty %)))
(s/def :field.legal-entity/type (s/and keyword? #(contains? #{:individual :company} %)))

(s/def :field/legal-entity (s/keys :opt [:field.legal-entity/address
                                         :field.legal-entity/dob
                                         :field.legal-entity/first-name
                                         :field.legal-entity/last-name
                                         :field.legal-entity/personal-id-number
                                         :field.legal-entity/type]))

(s/def :account/activate (s/keys :opt [:field/legal-entity
                                       :field/external-account]))

(s/def :field.external-account/transit-number (s/and string? #(= 5 (count %)) #(number? (c/parse-long-safe %))))
(s/def :field.external-account/institution-number (s/and string? #(= 3 (count %)) #(number? (c/parse-long-safe %))))
(s/def :field.external-account/account-number (s/and string? #(number? (c/parse-long-safe %))))

(s/def :field/external-account (s/keys :opt [:field.external-account/transit-number
                                           :field.external-account/institution-number
                                           :field.external-account/account-number]))
