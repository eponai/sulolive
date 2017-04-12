(ns eponai.common.ui.store.account.activate
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.store.account.validate :as v]
    [eponai.common.ui.elements.input-validate :as v-input]
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common :as c]))

;(def stripe-verifications
;  {:account.business.address/street "legal_entity.address.line1"
;   :account.business.address/postal "legal_entity.address.postal_code"
;   :account.business.address/city   "legal_entity.address.city"
;   :account.business.address/state  "legal_entity.address.state"
;
;   :account.business/name           "legal_entity.business_name"
;   :account.business/tax-id         "legal_entity.business_tax_id"
;
;   :account.personal.dob/day        "legal_entity.dob.day"
;   :account.personal.dob/month      "legal_entity.dob.month"
;   :account.personal.dob/year       "legal_entity.dob.year"
;
;   :account.personal/first-name     "legal_entity.first_name"
;   :account.personal/last-name      "legal_entity.last_name"
;
;   :account/bank-account            "external_account"})


(defn minimum-fields [spec type]
  (cond (= type :individual)
        (get-in spec [:country-spec/verification-fields :country-spec.verification-fields/individual :country-spec.verification-fields.individual/minimum])
        (= type :company)
        (get-in spec [:country-spec/verification-fields :country-spec.verification-fields/company :country-spec.verification-fields.company/minimum])))

(defn field-required? [fields k]
  (let [id (get v/stripe-verifications k)]
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
  #?(:cljs
     (activate-account
       [this]
       (let [{:query/keys [stripe-country-spec]} (om/props this)
             {:keys [entity-type]} (om/get-state this)
             street (utils/input-value-by-id (:account.business.address/street v/form-inputs))
             postal (utils/input-value-by-id (:account.business.address/postal v/form-inputs))
             city (utils/input-value-by-id (:account.business.address/city v/form-inputs))
             state (utils/input-value-by-id (:account.business.address/state v/form-inputs))

             year (c/parse-long-safe (utils/input-value-by-id (:account.personal.dob/year v/form-inputs)))
             month (c/parse-long-safe (utils/input-value-by-id (:account.personal.dob/month v/form-inputs)))
             day (c/parse-long-safe (utils/input-value-by-id (:account.personal.dob/day v/form-inputs)))

             first-name (utils/input-value-by-id (:account.personal/first-name v/form-inputs))
             last-name (utils/input-value-by-id (:account.personal/last-name v/form-inputs))

             transit (utils/input-value-by-id (:account.bank-account/transit-number v/form-inputs))
             institution (utils/input-value-by-id (:account.bank-account/institution-number v/form-inputs))
             account (c/parse-long-safe (utils/input-value-by-id (:account.bank-account/account-number v/form-inputs)))

             validation (v/validate {:account.business/address {:account.business.address/street street
                                                                :account.business.address/postal postal
                                                                :account.business.address/city   city
                                                                :account.business.address/state  state}
                                     :account.personal/dob {:account.personal.dob/year year
                                                            :account.personal.dob/month month
                                                            :account.personal.dob/day day}
                                     :account.personal/first-name first-name
                                     :account.personal/last-name last-name
                                     :account/bank-account {:account.bank-account/institution-number institution
                                                            :account.bank-account/transit-number transit
                                                            :account.bank-account/account-number account}})]
         (debug "Validation: " validation)
         (om/update-state! this assoc :input-validation validation))))
  (initLocalState [this]
    {:entity-type :individual})

  (render [this]
    (let [{:query/keys [stripe-country-spec]} (om/props this)
          {:keys [entity-type input-validation]} (om/get-state this)
          fields-needed (minimum-fields stripe-country-spec entity-type)]
      (debug "Country spec: " stripe-country-spec)
      (dom/div
        {:id "sulo-activate-account-form"}
        ;(dom/div
        ;  (css/callout)
        ;  (dom/p (css/add-class :header) "Where are you based?")
        ;  (grid/row
        ;    nil
        ;    (label-column
        ;      nil
        ;      (dom/label nil "Country"))
        ;    (grid/column
        ;      nil
        ;      (dom/select
        ;        {:defaultValue "ca"}
        ;        (dom/option {:value "ca"} "Canada")))))

        (dom/div
          (css/callout)
          (dom/p (css/add-class :header) "Account details")
          (grid/row
            nil
            (label-column
              nil
              (dom/label nil "Country"))
            (grid/column
              nil
              (dom/strong nil "Canada")))
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
                  (v-input/input
                    {:type        "text"
                     :placeholder "Company, LTD"
                     :id          (:account.business/name v/form-inputs)}
                    input-validation)))))
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
                     :placeholder "123123123"
                     :id (:account.business/tax-id v/form-inputs)})
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
                  (v-input/input
                    {:type        "text"
                     :placeholder "Street"
                     :id          (:account.business.address/street v/form-inputs)}
                    input-validation))
                (grid/row
                  nil
                  (when (field-required? fields-needed :account.business.address/postal)
                    (grid/column
                      nil
                      (v-input/input
                        {:type        "text"
                         :placeholder "Postal code"
                         :id          (:account.business.address/postal v/form-inputs)}
                        input-validation)))
                  (when (field-required? fields-needed :account.business.address/city)
                    (grid/column
                      nil
                      (v-input/input
                        {:type        "text"
                         :placeholder "City"
                         :id (:account.business.address/city v/form-inputs)}
                        input-validation)))
                  (when (field-required? fields-needed :account.business.address/state)
                    (grid/column
                      nil
                      (v-input/input
                        {:type        "text"
                         :placeholder "State"
                         :id (:account.business.address/state v/form-inputs)}
                        input-validation))))))))

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
                (v-input/input
                  {:type        "text"
                   :placeholder "First"
                   :id (:account.personal/first-name v/form-inputs)}
                  input-validation))
              (grid/column
                nil
                (v-input/input
                  {:type        "text"
                   :placeholder "Last"
                   :id (:account.personal/last-name v/form-inputs)}
                  input-validation))))

          (when (field-required? fields-needed :account.personal.dob/day)
            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Date of birth"))
              (grid/column
                nil
                (v-input/input
                  {:type        "text"
                   :placeholder "Month"
                   :id (:account.personal.dob/month v/form-inputs)}
                  input-validation))
              (grid/column
                nil
                (v-input/input
                  {:type        "text"
                   :placeholder "Day"
                   :id (:account.personal.dob/day v/form-inputs)}
                  input-validation))
              (grid/column
                nil
                (v-input/input
                  {:type        "text"
                   :placeholder "Year"
                   :id (:account.personal.dob/year v/form-inputs)}
                  input-validation)))))
        (when (field-required? fields-needed :account/external-account)
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
                (v-input/input
                  {:type        "text"
                   :placeholder "12345"
                   :id (:account.bank-account/transit-number v/form-inputs)}
                  input-validation)))
            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Institution number"))
              (grid/column
                nil
                (v-input/input
                  {:type        "text"
                   :placeholder "000"
                   :id (:account.bank-account/institution-number v/form-inputs)}
                  input-validation)))
            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Account number"))
              (grid/column
                nil
                (v-input/input
                  {:type        "text"
                   :placeholder ""
                   :id (:account.bank-account/account-number v/form-inputs)}
                  input-validation)))))
        (dom/div
          (->>  (css/callout)
                (css/text-align :right))
          (dom/p (css/add-class :header))
          ;(dom/a (css/button-hollow)
          ;       (dom/span nil "Save for later"))
          (dom/a
            (->> {:onClick #(.activate-account this)}
                 (css/button))
            (dom/span nil "Activate account"))
          (dom/p nil (dom/small nil "By activating your account, you agree to our Services Agreement.")))))))

(def ->Activate (om/factory Activate))
