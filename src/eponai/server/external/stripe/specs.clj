(ns eponai.server.external.stripe.specs
  (:require
    [clojure.spec :as s]
    [taoensso.timbre :refer [debug]]))


;; ###################################### SPECS

(s/def :ext.stripe.legal-entity.dob/year number?)
(s/def :ext.stripe.legal-entity.dob/month number?)
(s/def :ext.stripe.legal-entity.dob/day number?)
(s/def :ext.stripe.legal-entity/first_name string?)
(s/def :ext.stripe.legal-entity/last_name string?)
(s/def :ext.stripe.legal-entity/type (s/and string? #(contains? #{"individual" "company"} %)))

(s/def :ext.stripe/default_currency (s/and string? #(= 3 (count %))))

(s/def :ext.stripe.legal-entity/dob (s/keys :req-un [:ext.stripe.legal-entity.dob/year
                                                     :ext.stripe.legal-entity.dob/month
                                                     :ext.stripe.legal-entity.dob/day]))

(s/def :ext.stripe/legal_entity (s/keys :opt-un [:ext.stripe.legal-entity/first_name
                                                 :ext.stripe.legal-entity/last_name
                                                 :ext.stripe.legal-entity/type
                                                 :ext.stripe.legal-entity/dob]))

(s/def :ext.stripe.params/update-account (s/keys :opt-un
                                                 [:ext.stripe/legal_entity
                                                  :ext.stripe/default_currency
                                                  :ext.stripe/payout_schedule]))