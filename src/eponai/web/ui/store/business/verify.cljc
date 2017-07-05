(ns eponai.web.ui.store.business.verify
  (:require
    [eponai.common.format :as f]
    [eponai.common.ui.dom :as dom]
    [eponai.client.parser.message :as msg]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.store.business.specs :as specs]
    #?(:cljs
       [eponai.web.utils :as utils])
    #?(:cljs [eponai.web.ui.stripe :as stripe])
    [eponai.common.ui.elements.input-validate :as v]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.client.routes :as routes]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.script-loader :as script-loader]
    [eponai.web.s3-uploader :as s3]
    [eponai.common.format.date :as date]
    [clojure.string :as string]
    [eponai.common.ui.elements.menu :as menu]
    #?(:cljs
       [cljs-http.client :as http])
    [eponai.common.shared :as shared]
    [eponai.web.ui.button :as button]))

(def stripe-fields
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

   :field.legal-entity/document           "legal_entity.verification.document"

   :field/external-account                "external_account"})

(def form-inputs
  (merge stripe-fields
         {:field.external-account/currency           "external_account.currency"
          :field.external-account/country            "external_account.country"
          :field.external-account/transit-number     "external_account.transit_number"
          :field.external-account/institution-number "external_account.institution_number"
          :field.external-account/account-number     "external_account.account_number"

          :field.general/email                       "general.email"
          :field.general/store-name                  "general.store-name"
          :field.general/store-tagline               "general.store-tagline"}))

(defn label-column [opts & content]
  (grid/column
    (grid/column-size {:small 12 :large 3} opts)
    content))

(defn minimum-fields [spec type]
  (cond (= type :individual)
        (get-in spec [:country-spec/verification-fields :country-spec.verification-fields/individual :country-spec.verification-fields.individual/minimum])
        (= type :company)
        (get-in spec [:country-spec/verification-fields :country-spec.verification-fields/company :country-spec.verification-fields.company/minimum])))

(defn required-fields [component]
  (let [{:query/keys [stripe-country-spec]} (om/props component)
        {:keys [entity-type]} (om/get-state component)
        {:keys [stripe-account]} (om/get-computed component)
        fields-needed-for-country (minimum-fields stripe-country-spec entity-type)
        fields-needed-for-account (get-in stripe-account [:stripe/verification :stripe.verification/fields-needed])]

    (if (:stripe/details-submitted? stripe-account)
      (filter #(some? (get (set fields-needed-for-account) %)) (vals stripe-fields))
      (filter #(some? (get (set fields-needed-for-country) %)) (vals stripe-fields)))))

(defn account-details [component]
  (let [{:keys [entity-type input-validation]} (om/get-state component)
        {:keys [stripe-account store]} (om/get-computed component)
        all-required-fields (set (required-fields component))
        account-fields (into #{}
                             (filter #(some? (get all-required-fields (get form-inputs %)))
                                     [:field.legal-entity/type
                                      :field.legal-entity/business-name
                                      :field.legal-entity/business-tax-id
                                      :field.legal-entity.address/line1
                                      :field.legal-entity.address/city
                                      :field.legal-entity.address/postal
                                      :field.legal-entity.address/state]))]
    (debug "Account fields: " account-fields)
    (when (not-empty account-fields)
      [
       (dom/p (css/add-class :section-title) "Account details")
       (menu/vertical
         (css/add-class :section-list)
         (menu/item
           nil
           (grid/row
             nil
             (label-column
               nil
               (dom/label nil "Country"))
             (grid/column
               nil
               (dom/strong nil (string/upper-case (:stripe/country stripe-account))))))

         (when (get account-fields :field.legal-entity/type)
           (menu/item
             nil
             (grid/row
               nil
               (label-column
                 nil
                 (dom/label nil "Business type"))
               (grid/column
                 nil
                 (dom/input {:type     "radio"
                             :name     "entity-type"
                             :id       "entity-type-individual"
                             :value    "individual"
                             :checked  (= entity-type :individual)
                             :onChange #(om/update-state! component assoc :entity-type :individual)})
                 (dom/label {:htmlFor "entity-type-individual"} "Individual")
                 (dom/input {:type     "radio"
                             :name     "entity-type"
                             :id       "entity-type-company"
                             :value    "company"
                             :checked  (= entity-type :company)
                             :onChange #(om/update-state! component assoc :entity-type :company)})
                 (dom/label {:htmlFor "entity-type-company"} "Company")))))

         (when (get account-fields :field.legal-entity/business-name)
           (menu/item
             nil
             (grid/row
               nil
               (label-column
                 nil
                 (dom/label nil "Legal name"))
               (grid/column
                 nil
                 (v/input
                   {:type        "text"
                    :placeholder "Company, LTD"
                    :id          (:field.legal-entity/business-name form-inputs)}
                   input-validation)))))

         (when (get account-fields :field.legal-entity/business-tax-id)
           (menu/item
             nil
             (grid/row
               nil
               (label-column
                 nil
                 (dom/label nil "Business number (Tax ID)"))
               (grid/column
                 nil
                 (dom/input
                   {:type        "text"
                    :placeholder "123123123"
                    :id          (:field.legal-entity/business-tax-id form-inputs)})
                 (dom/small nil "We only need your 9-digit Business Number. Don't have one yet? Apply online.")))))

         (when (some #(contains? account-fields %) [:field.legal-entity.address/line1
                                                    :field.legal-entity.address/postal
                                                    :field.legal-entity.address/city
                                                    :field.legal-entity.address/state])
           (menu/item
             nil
             (grid/row
               nil
               (label-column
                 nil
                 (dom/label nil "Business address"))
               (grid/column
                 nil
                 (when (get account-fields :field.legal-entity.address/line1)
                   (grid/row
                     nil
                     (grid/column
                       (grid/column-size {:small 12 :medium 8})
                       (v/input
                         {:type        "text"
                          :placeholder "Street"
                          :id          (:field.legal-entity.address/line1 form-inputs)}
                         input-validation))
                     (grid/column
                       nil
                       (v/input
                         {:type        "text"
                          :placeholder "Apt/Suite/Other"
                          :id          (:field.legal-entity.address/line2 form-inputs)}
                         input-validation))))
                 (grid/row
                   nil
                   (when (get account-fields :field.legal-entity.address/postal)
                     (grid/column
                       nil
                       (v/input
                         {:type        "text"
                          :placeholder "Postal code"
                          :id          (:field.legal-entity.address/postal form-inputs)}
                         input-validation)))
                   (when (get account-fields :field.legal-entity.address/city)
                     (grid/column
                       nil
                       (v/input
                         {:type        "text"
                          :placeholder "City"
                          :id          (:field.legal-entity.address/city form-inputs)}
                         input-validation)))
                   (when (get account-fields :field.legal-entity.address/state)
                     (grid/column
                       nil
                       (v/input
                         {:type        "text"
                          :placeholder "State"
                          :id          (:field.legal-entity.address/state form-inputs)}
                         input-validation)))))))))])))

(defn personal-details [component]
  (let [{:keys [input-validation entity-type document-upload uploaded-document]} (om/get-state component)
        all-required-fields (set (required-fields component))
        {:keys [store]} (om/get-computed component)
        personal-fields (into #{}
                              (filter #(some? (get all-required-fields (get form-inputs %)))
                                      [:field.legal-entity/first-name
                                       :field.legal-entity/last-name
                                       :field.legal-entity.dob/day
                                       :field.legal-entity.dob/month
                                       :field.legal-entity.dob/year
                                       :field.legal-entity/personal-id-number
                                       :field.legal-entity/document]))]


    (when (not-empty personal-fields)
      [(dom/p (css/add-class :section-title) (if (= entity-type :individual)
                                               "Your personal details"
                                               "You, the company representative"))
       (menu/vertical
         (css/add-class :section-list)
         (when (get personal-fields :field.legal-entity/first-name)
           (menu/item
             nil
             (grid/row
               nil
               (label-column
                 nil
                 (dom/label nil "Legal name"))
               (grid/column
                 nil
                 (v/input
                   {:type        "text"
                    :placeholder "First"
                    :id          (:field.legal-entity/first-name form-inputs)}
                   input-validation))
               (grid/column
                 nil
                 (v/input
                   {:type        "text"
                    :placeholder "Last"
                    :id          (:field.legal-entity/last-name form-inputs)}
                   input-validation)))))

         (when (get personal-fields :field.legal-entity.dob/day)
           (menu/item
             nil
             (grid/row
               nil
               (label-column
                 nil
                 (dom/label nil "Date of birth"))
               (grid/column
                 nil
                 (v/input
                   {:type        "text"
                    :placeholder "Month"
                    :id          (:field.legal-entity.dob/month form-inputs)}
                   input-validation))
               (grid/column
                 nil
                 (v/input
                   {:type        "text"
                    :placeholder "Day"
                    :id          (:field.legal-entity.dob/day form-inputs)}
                   input-validation))
               (grid/column
                 nil
                 (v/input
                   {:type        "text"
                    :placeholder "Year"
                    :id          (:field.legal-entity.dob/year form-inputs)}
                   input-validation)))))
         (when (get personal-fields :field.legal-entity/personal-id-number)
           (menu/item
             nil
             (grid/row
               nil
               (label-column
                 nil
                 (dom/label nil "SIN (Tax ID)"))
               (grid/column
                 nil
                 (v/input
                   {:type        "text"
                    :placeholder ""
                    :id          (:field.legal-entity/personal-id-number form-inputs)}
                   input-validation)
                 (dom/small nil "Stripe require identification to confirm you are a representative of this business, and don't use this for any other purpose.")))))
         (when (get personal-fields :field.legal-entity/document)
           (menu/item
             (css/add-class :document-upload-container)
             (grid/row
               nil
               (grid/column
                 nil
                 (dom/label nil "Verification document")
                 (dom/p nil (dom/small nil "Upload a photo copy of your identifying document, such as a passport or driver’s license.")))
               (grid/column
                 (->> (css/add-class :document-uploader)
                      (css/text-align :right))

                 (s3/->FileUploader (om/computed {:id (:field.legal-entity/document form-inputs)}
                                                 {:on-upload (fn [{:keys [file response error-message]}]
                                                               (debug "Changed to file: " file)
                                                               (om/update-state! component assoc :document-upload
                                                                                 {:error?        (some? error-message)
                                                                                  :error-message error-message
                                                                                  :file          file
                                                                                  :response      response}))
                                                  :owner     store}))
                 ;(dom/input
                 ;  (css/show-for-sr {:type        "file"
                 ;                    :placeholder "First"
                 ;                    :accept      "image/png,image/jpeg"
                 ;                    :onChange    #(.select-document component %)
                 ;                    :id          (:field.legal-entity/document form-inputs)}))
                 (when (:error uploaded-document)
                   (dom/p (css/add-class :text-alert)
                          (dom/small nil "Something went wrong uploading your file to Stripe 🙁")))
                 (dom/p (when (:error? document-upload)
                          (css/add-class :text-alert))
                        (if (not-empty document-upload)
                          (dom/small nil (if (:error? document-upload)
                                           (str (:error-message document-upload))
                                           (str (some-> (:file document-upload) (.-name)))))
                          (dom/small nil (dom/i nil "No file selected"))))
                 (dom/div
                   nil
                   (dom/label
                     {:htmlFor (:field.legal-entity/document form-inputs)}
                     (if (not-empty document-upload)
                       "Change file"
                       "Upload file"))))))))])))

(defn external-account [component]
  (let [{:query/keys [stripe-country-spec]} (om/props component)
        {:keys [input-validation]} (om/get-state component)
        all-required-fields (set (required-fields component))
        external-account-required? (get all-required-fields (get form-inputs :field/external-account))]
    (when external-account-required?
      [(dom/p (css/add-class :section-title) "Bank details")
       (menu/vertical
         (css/add-class :section-list)
         (menu/item
           nil
           (grid/row
             nil
             (grid/column
               nil
               (dom/p nil (dom/small nil "You can accept CAD and USD. Currencies without a bank account will be converted and sent to your default bank account. You can add more bank accounts later."))))
           (grid/row
             nil
             (label-column
               nil
               (dom/label nil "Bank account currency"))
             (grid/column
               nil
               (dom/select
                 {:defaultValue (:country-spec/default-currency stripe-country-spec)
                  :id           (:field.external-account/currency form-inputs)}
                 (map (fn [[k v]]
                        (let [code (when k (name k))]
                          (dom/option {:value code} (string/upper-case code))))
                      (:country-spec/supported-bank-account-currencies stripe-country-spec)))))
           (grid/row
             nil
             (label-column
               nil
               (dom/label nil "Transit number"))
             (grid/column
               nil
               (v/input
                 {:type        "text"
                  :placeholder "12345"
                  :id          (:field.external-account/transit-number form-inputs)}
                 input-validation)))
           (grid/row
             nil
             (label-column
               nil
               (dom/label nil "Institution number"))
             (grid/column
               nil
               (v/input
                 {:type        "text"
                  :placeholder "000"
                  :id          (:field.external-account/institution-number form-inputs)}
                 input-validation)))
           (grid/row
             nil
             (label-column
               nil
               (dom/label nil "Account number"))
             (grid/column
               nil
               (v/input
                 {:type        "text"
                  :placeholder ""
                  :id          (:field.external-account/account-number form-inputs)}
                 input-validation)))))])))

(defui Verify-no-loader
  static om/IQuery
  (query [this]
    [:query/stripe-country-spec
     :query/messages])

  Object
  (activate-account [this]
    #?(:cljs
       (let [{:query/keys [stripe-country-spec]} (om/props this)
             {:keys [store stripe-account]} (om/get-computed this)
             {:keys [entity-type document-upload]} (om/get-state this)
             street (utils/input-value-by-id (:field.legal-entity.address/line1 form-inputs))
             postal (utils/input-value-by-id (:field.legal-entity.address/postal form-inputs))
             city (utils/input-value-by-id (:field.legal-entity.address/city form-inputs))
             state (utils/input-value-by-id (:field.legal-entity.address/state form-inputs))

             year (utils/input-value-by-id (:field.legal-entity.dob/year form-inputs))
             month (utils/input-value-by-id (:field.legal-entity.dob/month form-inputs))
             day (utils/input-value-by-id (:field.legal-entity.dob/day form-inputs))

             first-name (utils/input-value-by-id (:field.legal-entity/first-name form-inputs))
             last-name (utils/input-value-by-id (:field.legal-entity/last-name form-inputs))
             personal-id-number (utils/input-value-by-id (:field.legal-entity/personal-id-number form-inputs))

             currency (utils/selected-value-by-id (:field.external-account/currency form-inputs))
             transit (utils/input-value-by-id (:field.external-account/transit-number form-inputs))
             institution (utils/input-value-by-id (:field.external-account/institution-number form-inputs))
             account (utils/input-value-by-id (:field.external-account/account-number form-inputs))

             input-map (f/remove-nil-keys
                         {:field/legal-entity     (f/remove-nil-keys
                                                    {:field.legal-entity/address            (f/remove-nil-keys
                                                                                              {:field.legal-entity.address/line1  street
                                                                                               :field.legal-entity.address/postal postal
                                                                                               :field.legal-entity.address/city   city
                                                                                               :field.legal-entity.address/state  state})
                                                     :field.legal-entity/dob                (f/remove-nil-keys
                                                                                              {:field.legal-entity.dob/year  year
                                                                                               :field.legal-entity.dob/month month
                                                                                               :field.legal-entity.dob/day   day})
                                                     :field.legal-entity/type               entity-type
                                                     :field.legal-entity/first-name         first-name
                                                     :field.legal-entity/last-name          last-name
                                                     :field.legal-entity/personal-id-number personal-id-number})
                          :field/external-account (f/remove-nil-keys
                                                    {:field.external-account/institution-number institution
                                                     :field.external-account/transit-number     transit
                                                     :field.external-account/account-number     account})})
             validation (v/validate :account/activate input-map form-inputs)]
         ;; TODO: Validate document upload
         (debug "Validation: " validation)

         (when (nil? validation)
           (when (and (some? (:response document-upload))
                      (not (:error? document-upload)))
             (let [{:keys [bucket key etag]} (:response document-upload)
                   file (:file document-upload)
                   file-type (.-type file)
                   file-size (.-size file)]
               (msg/om-transact! this `[(~'stripe/upload-identity-document
                                          ~{:store-id  (:db/id store)
                                            :bucket    bucket
                                            :key       key
                                            :etag      etag
                                            :file-size file-size
                                            :file-type file-type})])))
           ;; response:
           ;; {:location <full-url>
           ;;  :bucket <bucket>
           ;;  :key <key>
           ;;  :etag <etag>}
           (if (some? (:field/external-account input-map))
             (stripe/bank-account (shared/by-key this :shared/stripe)
                                  {:country        (:stripe/country stripe-account)
                                   :currency       currency
                                   :routing_number (str transit institution)
                                   :account_number account}
                                  {:on-success (fn [token ip]
                                                 (let [tos-acceptance {:field.tos-acceptance/ip   ip
                                                                       :field.tos-acceptance/date (date/current-secs)}]
                                                   (msg/om-transact! this `[(~'stripe/update-account
                                                                              ~{:account-params (-> input-map
                                                                                                    (assoc :field/external-account token)
                                                                                                    (assoc :field/tos-acceptance tos-acceptance))
                                                                                :store-id       (:db/id store)})
                                                                            :query/stripe-account])))})
             (msg/om-transact! this `[(~'stripe/update-account
                                        ~{:account-params input-map
                                          :store-id       (:db/id store)})
                                      :query/stripe-account])))

         (om/update-state! this assoc :input-validation validation))))

  (try-exit-verify [this]
    (let [{:keys [store]} (om/get-computed this)]
      (when-not (msg/any-messages? this ['stripe/update-account 'stripe/upload-identity-document])
        (routes/set-url! this :store-dashboard {:store-id (:db/id store)}))))

  (initLocalState [this]
    {:entity-type :individual})

  (componentDidUpdate [this prev-state prev-props]
    (let [message (msg/last-message this 'stripe/update-account)
          {:keys [store]} (om/get-computed this)]
      (when (msg/final? message)
        (when (msg/success? message)
          (msg/clear-messages! this 'stripe/update-account)
          (.try-exit-verify this))))

    (let [upload-msg (msg/last-message this 'stripe/upload-identity-document)]
      (when (msg/final? upload-msg)
        (let [upload-response (msg/message upload-msg)]
          (debug "Got upload-response: " upload-response)
          (if (msg/success? upload-msg)
            (do
              (msg/clear-messages! this 'stripe/upload-identity-document)
              (.try-exit-verify this))
            (om/update-state! this assoc
                              :uploaded-document (select-keys upload-response [:error])))))))
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
          nil
          (dom/p (css/add-class :section-title) "What's this?")
          (menu/vertical
            (css/add-class :section-list)
            (menu/item
              nil
              (grid/row
                nil
                (grid/column
                  nil
                  (dom/p nil
                         (dom/small nil "SULO Live is using Stripe under the hood to handle orders, payments and transfers for you.
                 The information requested in this form is required by Stripe for verification to keep payments and transfers enabled on your account. ")
                         (dom/a {:href   "https://stripe.com/docs/connect/identity-verification"
                                 :target "_blank"}
                                (dom/small nil "Learn more"))
                         (dom/br nil) (dom/br nil)
                         (dom/small nil "We don't use this information for any other purpose than to pass along to Stripe and let you manage your account. Your provided account details are reviewed by Stripe to ensure they comply with their terms of service. If there's a problem, we'll get in touch right away to resolve it as quickly as possible.")))))))

        (dom/div
          nil
          (account-details this)

          (personal-details this)
          (external-account this))

        (dom/hr nil)
        (dom/div
          (css/text-align :right)
          (dom/div
            (css/add-class :action-buttons)
            (button/store-navigation-cta
              {:onClick #(.activate-account this)}
              (dom/span nil "Submit")))
          (dom/p nil
                 (dom/small nil "By submitting, you agree to our ")
                 (dom/a {:href   (routes/url :tos)
                         :target "_blank"} (dom/small nil "Terms of service."))))))))

(def Verify (script-loader/stripe-loader Verify-no-loader))

(def ->Verify (om/factory Verify))