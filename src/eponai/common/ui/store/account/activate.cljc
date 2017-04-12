(ns eponai.common.ui.store.account.activate
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]))

;"external_account"
;1"legal_entity.address.city"
;2"legal_entity.address.line1"
;3"legal_entity.address.postal_code"
;4"legal_entity.address.state"
;5"legal_entity.business_name"
;6"legal_entity.business_tax_id"
;7"legal_entity.dob.day"
;8"legal_entity.dob.month"
;9"legal_entity.dob.year"
;10"legal_entity.first_name"
;11"legal_entity.last_name"
;12"legal_entity.personal_id_number"
;13"legal_entity.type"
;14"tos_acceptance.date"
;15"tos_acceptance.ip"
;(def stripe-fields
;  {"external_account"                 {:section :bank-details
;                                       :label "Bank account"}
;
;   "legal_entity.address.city"        {:section :account.business/address}
;   "legal_entity.address.line1"       {:section :account.business/address}
;   "legal_entity.address.postal_code" {:section :account.business/address}
;   "legal_entity.address.state"       {:section :account.business/address}
;
;   "legal_entity.dob.day"             {:section :account.personal/dob}
;   "legal_entity.dob.month"           {:section :account.personal/dob}
;   "legal_entity.dob.year"            {:section :account.personal/dob}
;   "legal_entity.first_name"          {:section :account.personal/legal-name}
;   "legal_entity.last_name"           {:section :account.personal/legal-name}
;   "legal_entity.personal_id_number"  {:section :account.personal/info}
;   "legal_entity.type"                {:section :account/business}
;   "tos_acceptance.date"              {:section :tos}
;   "tos_acceptance.ip"                {:section :tos}})

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

   :account/bank-account            "external_account"})


(defn minimum-fields [spec type]
  (cond (= type :individual)
        (get-in spec [:country-spec/verification-fields :country-spec.verification-fields/individual :country-spec.verification-fields.individual/minimum])
        (= type :company)
        (get-in spec [:country-spec/verification-fields :country-spec.verification-fields/company :country-spec.verification-fields.company/minimum])))

(defn field-required? [fields k]
  (let [id (get stripe-verifications k)]
    (debug "fields: " fields)
    (boolean (some #(when (= % id) %) fields))))

(defn label-column [opts & content]
  (grid/column
    (grid/column-size {:small 12 :large 3} opts)
    content))

(defui Activate
  static om/IQuery
  (query [this]
    [:query/stripe-country-spec])

  Object
  (initLocalState [this]
    {:entity-type :individual})

  (render [this]
    (let [{:query/keys [stripe-country-spec]} (om/props this)
          {:keys [entity-type]} (om/get-state this)
          fields-needed (minimum-fields stripe-country-spec entity-type)]
      (debug "Country spec: " stripe-country-spec)
      (dom/div
        {:id "sulo-activate-account-form"}
        (dom/div
          (css/callout)
          (dom/p (css/add-class :header) "Where are you based?")
          (grid/row
            nil
            (label-column
              nil
              (dom/label nil "Country"))
            (grid/column
              nil
              (dom/select
                {:defaultValue "ca"}
                (dom/option {:value "ca"} "Canada")))))

        (dom/div
          (css/callout)
          (dom/p (css/add-class :header) "Account details")
          (grid/row
            nil
            (label-column
              nil
              (dom/label nil "Business type"))
            (grid/column
              (css/add-class :business-type)
              (dom/input {:type     "radio"
                          :name     "entity-type"
                          :id       "entity-type-individual"
                          :value    "individual"
                          :checked  (= entity-type :individual)
                          :onChange #(om/update-state! this assoc :entity-type :individual)})
              (dom/label {:htmlFor "entity-type-individual"} "Individual")
              (dom/input {:type     "radio"
                          :name     "entity-type"
                          :id       "entity-type-company"
                          :value    "company"
                          :checked  (= entity-type :company)
                          :onChange #(om/update-state! this assoc :entity-type :company)})
              (dom/label {:htmlFor "entity-type-company"} "Company")
              ))
          (when (field-required? fields-needed :account.business/name)
            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Legal name"))
              (grid/column
                nil
                (grid/row-column
                  nil
                  (dom/input
                    {:type        "text"
                     :placeholder "Company, LTD"})))))
          (when (field-required? fields-needed :account.business/tax-id)
            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Business number (Tax ID)"))
              (grid/column
                nil
                (grid/row-column
                  nil
                  (dom/input
                    {:type        "text"
                     :placeholder "123123123"})
                  (dom/small nil "We only need your 9-digit Business Number. Don't have one yet? Apply online.")))))
          (when (field-required? fields-needed :account.business.address/street)
            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Business address"))
              (grid/column
                nil
                (grid/row-column
                  nil
                  (dom/input
                    {:type        "text"
                     :placeholder "Street"}))
                (grid/row
                  nil
                  (when (field-required? fields-needed :account.business.address/postal)
                    (grid/column
                      nil
                      (dom/input
                        {:type        "text"
                         :placeholder "Postal code"})))
                  (when (field-required? fields-needed :account.business.address/city)
                    (grid/column
                      nil
                      (dom/input
                        {:type        "text"
                         :placeholder "City"})))
                  (when (field-required? fields-needed :account.business.address/state)
                    (grid/column
                      nil
                      (dom/input
                        {:type        "text"
                         :placeholder "State"}))))))))

        (dom/div
          (css/callout)
          (dom/p (css/add-class :header)
                 (if (= entity-type :individual)
                   "Your personal details"
                   "You, the company representative"))
          (when (field-required? fields-needed :account.personal/first-name)
            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Legal name"))
              (grid/column
                nil
                (dom/input
                  {:type        "text"
                   :placeholder "First"}))
              (grid/column
                nil
                (dom/input
                  {:type        "text"
                   :placeholder "Last"}))))

          (when (field-required? fields-needed :account.personal.dob/day)
            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Date of birth"))
              (grid/column
                nil
                (dom/input
                  {:type        "text"
                   :placeholder "Month"}))
              (grid/column
                nil
                (dom/input
                  {:type        "text"
                   :placeholder "Day"}))
              (grid/column
                nil
                (dom/input
                  {:type        "text"
                   :placeholder "Year"})))))
        (when (field-required? fields-needed :account/bank-account)
          (dom/div
            (css/callout)
            (dom/p (css/add-class :header) "Bank details")
            (dom/p nil (dom/small nil "You can accept CAD and USD. Currencies without a bank account will be converted and sent to your default bank account. You can add bank accounts for other currencies later."))
            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Bank account currency"))
              (grid/column
                nil
                (dom/select
                  {:defaultValue (:country-spec/default-currency stripe-country-spec)}
                  (map (fn [cur]
                         (let [code (key cur)]
                           (dom/option {:value code} (.toUpperCase code))))
                       (:country-spec/supported-bank-account-currencies stripe-country-spec)))))
            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Transit number"))
              (grid/column
                nil
                (dom/input
                  {:type        "text"
                   :placeholder "12345"})))
            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Institution number"))
              (grid/column
                nil
                (dom/input
                  {:type        "text"
                   :placeholder "000"})))
            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Account number"))
              (grid/column
                nil
                (dom/input
                  {:type        "text"
                   :placeholder ""})))))
        (dom/div
          (->>  (css/callout)
                (css/text-align :right))
          (dom/p (css/add-class :header))
          (dom/a (css/button-hollow) "Save for later")
          (dom/a (css/button) "Activate account")
          (dom/p nil (dom/small nil "By activating your account, you agree to our Services Agreement.")))))))

(def ->Activate (om/factory Activate))
