(ns eponai.common.ui.store.account.activate
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.store.account.validate :as v]
    [eponai.common.ui.elements.input-validate :as v-input]
    [eponai.client.parser.message :as msg]
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common :as c]
    [eponai.common.format.date :as date]
    [eponai.client.routes :as routes]
    [eponai.common.format :as format]))

(def stripe-key "pk_test_VhkTdX6J9LXMyp5nqIqUTemM")

(defn minimum-fields [spec type]
  (cond (= type :individual)
        (get-in spec [:country-spec/verification-fields :country-spec.verification-fields/individual :country-spec.verification-fields.individual/minimum])
        (= type :company)
        (get-in spec [:country-spec/verification-fields :country-spec.verification-fields/company :country-spec.verification-fields.company/minimum])))

(defn field-required? [component k]
  (let [{:query/keys [stripe-country-spec]} (om/props component)
        {:keys [entity-type input-validation]} (om/get-state component)
        {:keys [stripe-account]} (om/get-computed component)
        fields-needed-for-country (minimum-fields stripe-country-spec entity-type)
        fields-needed-for-account (get-in stripe-account [:stripe/verification :stripe.verification/fields-needed])
        id (get v/stripe-verifications k)
        is-needed-for-country? (boolean (some #(when (= % id) %) fields-needed-for-country))
        is-needed-for-account? (boolean (some #(when (= % id) %) fields-needed-for-account))]
    ;; If user submitted details already, this means we are probably here because someone entered the URL, or more info is needed.
    ;; Check the account verification for that, to show the appropriate form
    (if (:stripe/details-submitted? stripe-account)
      is-needed-for-account?
      is-needed-for-country?)))

(defn label-column [opts & content]
  (grid/column
    (grid/column-size {:small 12 :large 3} opts)
    content))

(defui Activate
  static om/IQuery
  (query [this]
    [:query/stripe-country-spec
     :query/messages])

  Object
  #?(:cljs
     (activate-account
       [this]
       (let [{:query/keys [stripe-country-spec]} (om/props this)
             {:keys [store stripe-account]} (om/get-computed this)
             {:keys [entity-type]} (om/get-state this)
             street (utils/input-value-by-id (:field.legal-entity.address/line1 v/form-inputs))
             postal (utils/input-value-by-id (:field.legal-entity.address/postal v/form-inputs))
             city (utils/input-value-by-id (:field.legal-entity.address/city v/form-inputs))
             state (utils/input-value-by-id (:field.legal-entity.address/state v/form-inputs))

             year (utils/input-value-by-id (:field.legal-entity.dob/year v/form-inputs))
             month (utils/input-value-by-id (:field.legal-entity.dob/month v/form-inputs))
             day (utils/input-value-by-id (:field.legal-entity.dob/day v/form-inputs))

             first-name (utils/input-value-by-id (:field.legal-entity/first-name v/form-inputs))
             last-name (utils/input-value-by-id (:field.legal-entity/last-name v/form-inputs))
             personal-id-number (utils/input-value-by-id (:field.legal-entity/personal-id-number v/form-inputs))

             currency (utils/selected-value-by-id (:field.external-account/currency v/form-inputs))
             transit (utils/input-value-by-id (:field.external-account/transit-number v/form-inputs))
             institution (utils/input-value-by-id (:field.external-account/institution-number v/form-inputs))
             account (utils/input-value-by-id (:field.external-account/account-number v/form-inputs))

             input-map (format/remove-nil-keys
                         {:field/legal-entity     (format/remove-nil-keys
                                                    {:field.legal-entity/address            (format/remove-nil-keys
                                                                                              {:field.legal-entity.address/line1  street
                                                                                               :field.legal-entity.address/postal postal
                                                                                               :field.legal-entity.address/city   city
                                                                                               :field.legal-entity.address/state  state})
                                                     :field.legal-entity/dob                (format/remove-nil-keys
                                                                                              {:field.legal-entity.dob/year  year
                                                                                               :field.legal-entity.dob/month month
                                                                                               :field.legal-entity.dob/day   day})
                                                     :field.legal-entity/type               entity-type
                                                     :field.legal-entity/first-name         first-name
                                                     :field.legal-entity/last-name          last-name
                                                     :field.legal-entity/personal-id-number personal-id-number})
                          :field/external-account (format/remove-nil-keys
                                                    {:field.external-account/institution-number institution
                                                     :field.external-account/transit-number     transit
                                                     :field.external-account/account-number     account})})
             validation (v/validate :account/activate input-map)]
         (debug "Validation: " validation)
         (when (nil? validation)
           (if (some? (:field/external-account input-map))
             (do
               (.setPublishableKey js/Stripe stripe-key)
               (.createToken js/Stripe.bankAccount
                             #js {:country        (:stripe/country stripe-account)
                                  :currency       currency
                                  :routing_number (str transit institution)
                                  :account_number account}
                             (fn [status ^js/Stripe.bankAccountResponse response]
                               (when (= status 200)
                                 (let [token (.-id response)
                                       ip (.-client_ip response)
                                       tos-acceptance {:field.tos-acceptance/ip   ip
                                                       :field.tos-acceptance/date (/ (date/date->long (date/today)) 1000)}]
                                   (msg/om-transact! this `[(stripe/update-account
                                                              ~{:account-params (-> input-map
                                                                                    (assoc :field/external-account token)
                                                                                    (assoc :field/tos-acceptance tos-acceptance))
                                                                :store-id       (:db/id store)})
                                                            :query/stripe-account]))))))
             (msg/om-transact! this `[(stripe/update-account
                                        ~{:account-params input-map
                                          :store-id       (:db/id store)})
                                      :query/stripe-account])))

         (om/update-state! this assoc :input-validation validation))))
  (initLocalState [this]
    {:entity-type :individual})

  (componentDidUpdate [this prev-state prev-props]
    (let [message (msg/last-message this 'stripe/update-account)
          {:keys [store]} (om/get-computed this)]
      (when (msg/final? message)
        (when (msg/success? message)
          (routes/set-url! this :store-dashboard {:store-id (:db/id store)})))))
  (render [this]
    (let [{:query/keys [stripe-country-spec]} (om/props this)
          {:keys [entity-type input-validation]} (om/get-state this)
          {:keys [stripe-account]} (om/get-computed this)
          fields-needed (minimum-fields stripe-country-spec entity-type)
          message (msg/last-message this 'stripe/update-account)]

      (debug "Stripe account: " stripe-account)
      (debug "Country specs: " stripe-country-spec)
      (dom/div
        {:id "sulo-activate-account-form"}
        (cond (msg/final? message)
              (when-not (msg/success? message)
                (dom/div
                  (->> (css/callout)
                       (css/add-class :alert))
                  (msg/message message)))
              (msg/pending? message)
              (common/loading-spinner nil))
        (dom/div
          (css/callout)
          (dom/p (css/add-class :header) "What's this?")
          (dom/p nil
                 (dom/span nil "SULO Live is using Stripe under the hood to handle orders, payments and transfers for you.
                 The information requested in this form is required by Stripe for verification to keep payments and transfers enabled on your account. ")
                 (dom/a {:href "https://stripe.com/docs/connect/identity-verification"}
                        (dom/span nil "Learn more")))
          (dom/p nil "Your account details are reviewed by Stripe to ensure they comply with their terms of service. If there's a problem, we'll get in touch right away to resolve it as quickly as possible.")
          (dom/p nil "We don't use this information for any other purpose than to pass along to Stripe and let you manage your account."))
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
              (dom/strong nil (:stripe/country stripe-account))))
          (when (field-required? this :field.legal-entity/type)
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
                )))
          (when (field-required? this :field.legal-entity/business-name)
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
          (when (field-required? this :field.legal-entity/business-tax-id)
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
                     :id          (:field.legal-entity/business-tax-id v/form-inputs)})
                  (dom/small nil "We only need your 9-digit Business Number. Don't have one yet? Apply online.")))))
          (when (field-required? this :field.legal-entity.address/line1)
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
                  (when (field-required? this :field.legal-entity.address/postal)
                    (grid/column
                      nil
                      (v-input/input
                        {:type        "text"
                         :placeholder "Postal code"
                         :id          (:field.legal-entity.address/postal v/form-inputs)}
                        input-validation)))
                  (when (field-required? this :field.legal-entity.address/city)
                    (grid/column
                      nil
                      (v-input/input
                        {:type        "text"
                         :placeholder "City"
                         :id          (:field.legal-entity.address/city v/form-inputs)}
                        input-validation)))
                  (when (field-required? this :field.legal-entity.address/state)
                    (grid/column
                      nil
                      (v-input/input
                        {:type        "text"
                         :placeholder "State"
                         :id          (:field.legal-entity.address/state v/form-inputs)}
                        input-validation))))))))

        (dom/div
          (css/callout)
          (dom/p (css/add-class :header)
                 (if (= entity-type :individual)
                   "Your personal details"
                   "You, the company representative"))
          (when (field-required? this :field.legal-entity/first-name)
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
                   :id          (:field.legal-entity/first-name v/form-inputs)}
                  input-validation))
              (grid/column
                nil
                (v-input/input
                  {:type        "text"
                   :placeholder "Last"
                   :id          (:field.legal-entity/last-name v/form-inputs)}
                  input-validation))))

          (when (field-required? this :field.legal-entity.dob/day)
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
                   :id          (:field.legal-entity.dob/month v/form-inputs)}
                  input-validation))
              (grid/column
                nil
                (v-input/input
                  {:type        "text"
                   :placeholder "Day"
                   :id          (:field.legal-entity.dob/day v/form-inputs)}
                  input-validation))
              (grid/column
                nil
                (v-input/input
                  {:type        "text"
                   :placeholder "Year"
                   :id          (:field.legal-entity.dob/year v/form-inputs)}
                  input-validation))))
          (when (field-required? this :field.legal-entity/personal-id-number)
            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "SIN (Tax ID)"))
              (grid/column
                nil
                (v-input/input
                  {:type        "text"
                   :placeholder ""
                   :id          (:field.legal-entity/personal-id-number v/form-inputs)}
                  input-validation)
                (dom/small nil "Stripe require identification to confirm you are a representative of this business, and don't use this for any other purpose.")))))
        (when (field-required? this :field/external-account)
          (dom/div
            (css/callout)
            (dom/p (css/add-class :header) "Bank details")
            (dom/p nil "You can accept CAD and USD. Currencies without a bank account will be converted and sent to your default bank account. You can add more bank accounts later.")
            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Bank account currency"))
              (grid/column
                nil
                (dom/select
                  {:defaultValue (:country-spec/default-currency stripe-country-spec)
                   :id (:field.external-account/currency v/form-inputs)}
                  (map (fn [cur]
                         (let [^String code (key cur)]
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
                   :id          (:field.external-account/transit-number v/form-inputs)}
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
                   :id          (:field.external-account/institution-number v/form-inputs)}
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
                   :id          (:field.external-account/account-number v/form-inputs)}
                  input-validation)))))
        (dom/div
          (->> (css/callout)
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
