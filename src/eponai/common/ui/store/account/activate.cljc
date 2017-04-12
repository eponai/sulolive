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
             {:keys [store]} (om/get-computed this)
             {:keys [entity-type]} (om/get-state this)
             street (utils/input-value-by-id (:field.legal-entity.address/line1 v/form-inputs))
             postal (utils/input-value-by-id (:field.legal-entity.address/postal v/form-inputs))
             city (utils/input-value-by-id (:field.legal-entity.address/city v/form-inputs))
             state (utils/input-value-by-id (:field.legal-entity.address/state v/form-inputs))

             year (c/parse-long-safe (utils/input-value-by-id (:field.legal-entity.dob/year v/form-inputs)))
             month (c/parse-long-safe (utils/input-value-by-id (:field.legal-entity.dob/month v/form-inputs)))
             day (c/parse-long-safe (utils/input-value-by-id (:field.legal-entity.dob/day v/form-inputs)))

             first-name (utils/input-value-by-id (:field.legal-entity/first-name v/form-inputs))
             last-name (utils/input-value-by-id (:field.legal-entity/last-name v/form-inputs))

             transit (utils/input-value-by-id (:field.external-account/transit-number v/form-inputs))
             institution (utils/input-value-by-id (:field.external-account/institution-number v/form-inputs))
             account (c/parse-long-safe (utils/input-value-by-id (:field.external-account/account-number v/form-inputs)))

             input-map {:field/legal-entity {:field.legal-entity/address    {:field.legal-entity.address/line1  street
                                                                             :field.legal-entity.address/postal postal
                                                                             :field.legal-entity.address/city   city
                                                                             :field.legal-entity.address/state  state}
                                             :field.legal-entity/dob        {:field.legal-entity.dob/year  year
                                                                             :field.legal-entity.dob/month month
                                                                             :field.legal-entity.dob/day   day}
                                             :field.legal-entity/first-name first-name
                                             :field.legal-entity/last-name  last-name}
                        :field/external-account        {:field.external-account/institution-number institution
                                                        :field.external-account/transit-number     transit
                                                        :field.external-account/account-number     account}}

             validation (v/validate input-map)]
         (when (nil? validation)
           (om/transact! this `[(stripe/update-account ~{:account-params input-map
                                                         :store-id (:db/id store)})]))
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
          (when (field-required? fields-needed :field.legal-entity/business-name)
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
                     :id          (:field.legal-entity/business-name v/form-inputs)}
                    input-validation)))))
          (when (field-required? fields-needed :field.legal-entity/business-tax-id)
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
                     :id (:field.legal-entity/business-tax-id v/form-inputs)})
                  (dom/small nil "We only need your 9-digit Business Number. Don't have one yet? Apply online.")))))
          (when (field-required? fields-needed :field.legal-entity.address/line1)
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
                     :id          (:field.legal-entity.address/line1 v/form-inputs)}
                    input-validation))
                (grid/row
                  nil
                  (when (field-required? fields-needed :field.legal-entity.address/postal)
                    (grid/column
                      nil
                      (v-input/input
                        {:type        "text"
                         :placeholder "Postal code"
                         :id          (:field.legal-entity.address/postal v/form-inputs)}
                        input-validation)))
                  (when (field-required? fields-needed :field.legal-entity.address/city)
                    (grid/column
                      nil
                      (v-input/input
                        {:type        "text"
                         :placeholder "City"
                         :id (:field.legal-entity.address/city v/form-inputs)}
                        input-validation)))
                  (when (field-required? fields-needed :field.legal-entity.address/state)
                    (grid/column
                      nil
                      (v-input/input
                        {:type        "text"
                         :placeholder "State"
                         :id (:field.legal-entity.address/state v/form-inputs)}
                        input-validation))))))))

        (dom/div
          (css/callout)
          (dom/p (css/add-class :header)
                 (if (= entity-type :individual)
                   "Your personal details"
                   "You, the company representative"))
          (when (field-required? fields-needed :field.legal-entity/first-name)
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
                   :id (:field.legal-entity/first-name v/form-inputs)}
                  input-validation))
              (grid/column
                nil
                (v-input/input
                  {:type        "text"
                   :placeholder "Last"
                   :id (:field.legal-entity/last-name v/form-inputs)}
                  input-validation))))

          (when (field-required? fields-needed :field.legal-entity.dob/day)
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
                   :id (:field.legal-entity.dob/month v/form-inputs)}
                  input-validation))
              (grid/column
                nil
                (v-input/input
                  {:type        "text"
                   :placeholder "Day"
                   :id (:field.legal-entity.dob/day v/form-inputs)}
                  input-validation))
              (grid/column
                nil
                (v-input/input
                  {:type        "text"
                   :placeholder "Year"
                   :id (:field.legal-entity.dob/year v/form-inputs)}
                  input-validation)))))
        (when (field-required? fields-needed :field/external-account)
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
                   :id (:field.external-account/transit-number v/form-inputs)}
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
                   :id (:field.external-account/institution-number v/form-inputs)}
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
                   :id (:field.external-account/account-number v/form-inputs)}
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
