(ns eponai.devcards.account-verify
  (:require
    [eponai.web.ui.store.business.verify :as v]
    [eponai.web.ui.store.business :as b]

    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.server.external.stripe.stub :as stub]
    [eponai.common.ui.dom :as dom]
    [eponai.client.devtools]
    [eponai.server.external.stripe.format :as f])
  (:require-macros
    [devcards.core :refer [defcard dom-node]]))

(eponai.client.devtools/install-app)
(def parser
  (om/parser {:read (fn [env k p] {:value (get @(:state env) k)})}))

(defn reconciler [st]
  (om/reconciler {:parser parser
                  :state  (atom {:state st})}))

(def all-country-fields (vec (get-in stub/country-specs ["CA" :verification_fields :individual :minimum])))

(defui BusinessUI
  static om/IQuery
  (query [_]
    [:state])
  Object
  (render [this]
    (let [{:keys [country-specs stripe-account]} (:state (om/props this))]
      (dom/div
        {:id "sulo-store-dashboard"}
        ((om/factory v/Verify-no-loader) (om/computed {:query/stripe-country-spec (f/stripe->country-spec (get country-specs "CA"))}
                                                      {:stripe-account stripe-account}))))))
(defcard
  VerifyAccountAllFieldsNeeded
  (dom-node
    (fn [_ node]
      (let [st {:country-specs  stub/country-specs
                :stripe-account {:stripe/country            "CA"
                                 :stripe/details-submitted? false
                                 :stripe/verification       {:stripe.verification/fields-needed all-country-fields}}}]
        (om/add-root! (reconciler st) BusinessUI node)))))

(defcard
  VerifyAccountBusinessInfoProvided
  (dom-node
    (fn [_ node]
      (let [fields (remove #(#{"legal_entity.address.city"
                               "legal_entity.address.line1"
                               "legal_entity.address.postal_code"
                               "legal_entity.address.state"
                               "legal_entity.type"} %)
                           all-country-fields)
            st {:country-specs  stub/country-specs
                :stripe-account {:stripe/country            "CA"
                                 :stripe/details-submitted? true
                                 ;:stripe/legal-entity       {:stripe.legal-entity/address {:stripe}}
                                 :stripe/verification       {:stripe.verification/fields-needed fields}}}]
        (om/add-root! (reconciler st) BusinessUI node)))))

(defcard
  VerifyAccountTermsAcceptanceNeeded
  (dom-node
    (fn [_ node]
      (let [fields ["tos_acceptance.date" "tos_acceptance.ip"]
            st {:country-specs  stub/country-specs
                :stripe-account {:stripe/country            "CA"
                                 :stripe/details-submitted? true
                                 ;:stripe/legal-entity       {:stripe.legal-entity/address {:stripe}}
                                 :stripe/verification       {:stripe.verification/fields-needed fields}}}]
        (om/add-root! (reconciler st) BusinessUI node)))))

(defcard
  VerifyAccountSINAndTermsNeeded
  (dom-node
    (fn [_ node]
      (let [fields ["tos_acceptance.date" "tos_acceptance.ip" "legal_entity.personal_id_number"]
            st {:country-specs  stub/country-specs
                :stripe-account {:stripe/country            "CA"
                                 :stripe/details-submitted? true
                                 ;:stripe/legal-entity       {:stripe.legal-entity/address {:stripe}}
                                 :stripe/verification       {:stripe.verification/fields-needed fields}}}]
        (om/add-root! (reconciler st) BusinessUI node)))))

(defcard
  VerifyAccountIDDocumentNeeded
  (dom-node
    (fn [_ node]
      (let [fields ["legal_entity.verification.document"]
            st {:country-specs  stub/country-specs
                :stripe-account {:stripe/country            "CA"
                                 :stripe/details-submitted? true
                                 ;:stripe/legal-entity       {:stripe.legal-entity/address {:stripe}}
                                 :stripe/verification       {:stripe.verification/fields-needed fields}}}]
        (om/add-root! (reconciler st) BusinessUI node)))))

