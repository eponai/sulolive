(ns eponai.web.ui.store.business.specs
  (:require
    [clojure.spec :as s]
    [eponai.common :as c]))

(s/def :field.legal-entity.address/line1 (s/and string? #(not-empty %)))
(s/def :field.legal-entity.address/postal (s/and string? #(not-empty %)))
(s/def :field.legal-entity.address/city (s/and string? #(not-empty %)))
(s/def :field.legal-entity.address/state (s/and string? #(not-empty %)))

(s/def :field.legal-entity.dob/day #(number? (c/parse-long-safe %)))
(s/def :field.legal-entity.dob/month #(number? (c/parse-long-safe %)))
(s/def :field.legal-entity.dob/year #(number? (c/parse-long-safe %)))

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