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
  {:account.business.address/street "legal_entity.address.line1"
   :account.business.address/postal "legal_entity.address.postal_code"
   :account.business.address/city   "legal_entity.address.city"
   :account.business.address/state  "legal_entity.address.state"

   :account.business/name           "legal_entity.business_name"
   :account.business/tax-id         "legal_entity.business_tax_id"

   :account.personal.dob/day        "legal_entity.dob.day"
   :account.personal.dob/month      "legal_entity.dob.month"
   :account.personal.dob/year       "legal_entity.dob.year"

   :account.personal/first-name     "legal_entity.first_name"
   :account.personal/last-name      "legal_entity.last_name"

   :account/external-account            "external_account"})

(def form-inputs
  (merge stripe-verifications
         {:account.bank-account/transit-number     "external_account.transit_number"
          :account.bank-account/institution-number "external_account.institution_number"
          :account.bank-account/account-number     "external_account.account_number"}))

(defn validate [m]
  (when-let [err (s/explain-data :account/activate m)]
    (let [problems (::s/problems err)
          invalid-paths (map (fn [p]
                               (some #(get form-inputs %) p))
                             (map :path problems))]
      {:explain-data    err
       :invalid-paths invalid-paths})))

;; ############## Specs #########################

(s/def :account.business.address/street (s/and string? #(not-empty %)))
(s/def :account.business.address/postal (s/and string? #(not-empty %)))
(s/def :account.business.address/city (s/and string? #(not-empty %)))
(s/def :account.business.address/state (s/and string? #(not-empty %)))

(s/def :account.personal.dob/day number?)
(s/def :account.personal.dob/month number?)
(s/def :account.personal.dob/year number?)

(s/def :account.business/address (s/keys :opt [:account.business.address/street
                                               :account.business.address/postal
                                               :account.business.address/city
                                               :account.business.address/state]))

(s/def :account.personal/dob (s/keys :req [:account.personal.dob/day
                                           :account.personal.dob/month
                                           :account.personal.dob/year]))
(s/def :account.personal/first-name (s/and string? #(not-empty %)))
(s/def :account.personal/last-name (s/and string? #(not-empty %)))

(s/def :account/activate (s/keys :opt [:account.business/address
                                       :account.personal/dob
                                       :account.personal/first-name
                                       :account.personal/last-name
                                       :account/bank-account]))

(s/def :account.bank-account/transit-number (s/and string? #(= 5 (count %)) #(number? (c/parse-long-safe %))))
(s/def :account.bank-account/institution-number (s/and string? #(= 3 (count %)) #(number? (c/parse-long-safe %))))
(s/def :account.bank-account/account-number number?)

(s/def :account/bank-account (s/keys :opt [:account.bank-account/transit-number
                                           :account.bank-account/institution-number
                                           :account.bank-account/account-number]))
