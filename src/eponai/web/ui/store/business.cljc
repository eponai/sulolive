(ns eponai.web.ui.store.business
  (:require
    [eponai.client.routes :as routes]
    [eponai.client.parser.message :as msg]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.menu :as menu]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.web.ui.store.business.verify :as verify]
    [eponai.common.ui.elements.input-validate :as v]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.store.finances :as finances]
    [eponai.web.ui.button :as button]
    [clojure.string :as string]
    #?(:cljs [eponai.web.utils :as utils])
    [eponai.common.ui.common :as common]
    [eponai.common.format.date :as date]
    [eponai.common.format :as f]
    [eponai.common.mixpanel :as mixpanel]))

(def form-inputs verify/form-inputs)

(defn label-column [opts & content]
  (grid/column
    (grid/column-size {:small 12 :large 3} opts)
    content))

(def prefix-key "business-details-")

(defn prefixed-id [k]
  (str prefix-key (get form-inputs k)))

(defn edit-personal-modal [component]
  (let [
        ;{:keys [store]} (om/get-computed thi)
        on-close #(om/update-state! component dissoc :modal :input-validation :error-message)
        {:keys [input-validation entity-type error-message]} (om/get-state component)
        {:query/keys [stripe-account]} (om/props component)
        legal-entity (:stripe/legal-entity stripe-account)
        {:stripe.legal-entity/keys [address type first-name last-name business-name dob]} legal-entity
        {:stripe.legal-entity.address/keys [line1 postal city state]} address
        {:stripe.legal-entity.dob/keys [year month day]} dob
        entity-type (or entity-type type)]
    (common/modal
      {:on-close on-close
       :size     "tiny"}
      (dom/h4 (css/add-class :header) "Edit personal details")
      (dom/label nil "Legal name")
      (grid/row
        (css/add-class :collapse)
        (grid/column
          nil

          (v/input {:type         "text"
                    :defaultValue first-name
                    :id           (prefixed-id :field.legal-entity/first-name)
                    :placeholder  "First"}
                   input-validation))
        (grid/column
          nil
          (v/input {:type         "text"
                    :defaultValue last-name
                    :id           (prefixed-id :field.legal-entity/last-name)
                    :placeholder  "Last"}
                   input-validation)))
      (dom/label nil "Date of birth")
      (grid/row
        (css/add-class :collapse)
        (grid/column
          nil

          (v/input {:type         "text"
                    :defaultValue month
                    :id           (prefixed-id :field.legal-entity.dob/month)
                    :placeholder  "Month"}
                   input-validation))
        (grid/column
          nil
          (v/input {:type         "text"
                    :defaultValue day
                    :id           (prefixed-id :field.legal-entity.dob/day)
                    :placeholder  "Day"}
                   input-validation))
        (grid/column
          nil
          (v/input {:type         "text"
                    :defaultValue year
                    :id           (prefixed-id :field.legal-entity.dob/year)
                    :placeholder  "Year"}
                   input-validation)))
      (dom/p (css/add-class :text-alert) (dom/small nil error-message))
      (dom/div
        (css/add-class :action-buttons)
        (button/cancel
          {:onClick on-close})
        (button/save
          {:onClick #(.save-personal-info component)})))))

(defn edit-business-modal [component]
  (let [
        ;{:keys [store]} (om/get-computed thi)
        on-close #(om/update-state! component dissoc :modal :input-validation :error-message)
        {:keys [input-validation entity-type error-message]} (om/get-state component)
        {:query/keys [stripe-account]} (om/props component)
        legal-entity (:stripe/legal-entity stripe-account)
        {:stripe.legal-entity/keys [address type first-name last-name business-name]} legal-entity
        {:stripe.legal-entity.address/keys [line1 postal city state]} address
        entity-type (or entity-type type)]
    (common/modal
      {:on-close on-close
       :size     "tiny"}
      (dom/h4 (css/add-class :header) "Edit business details")

      (dom/label nil "Business type")
      (grid/row
        (css/add-class :collapse)
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
          (dom/label {:htmlFor "entity-type-company"} "Company")))

      (when (= entity-type :company)
        [(dom/label nil "Legal name")
         (v/input {:type         "text"
                   :defaultValue business-name
                   :id           (prefixed-id :field.legal-entity/business-name)
                   :placeholder  "Company, LTD"}
                  input-validation)])
      (dom/label nil "Business Address")
      (v/input
        {:type         "text"
         :id           (prefixed-id :field.legal-entity.address/line1)
         :defaultValue line1
         :placeholder  "Street"}
        input-validation)

      (grid/row
        (css/add-class :collapse)
        (grid/column
          nil
          (v/input
            {:type         "text"
             :id           (prefixed-id :field.legal-entity.address/postal)
             :defaultValue postal
             :placeholder  "Postal Code"}
            input-validation))
        (grid/column
          nil
          (v/input
            {:type         "text"
             :id           (prefixed-id :field.legal-entity.address/city)
             :defaultValue city
             :placeholder  "City"}
            input-validation))
        (grid/column
          nil
          (v/input
            {:type         "text"
             :id           (prefixed-id :field.legal-entity.address/state)
             :defaultValue state
             :placeholder  "Province"}
            input-validation)))
      (dom/p (css/add-class :text-alert) (dom/small nil error-message))
      (dom/div
        (css/add-class :action-buttons)
        (button/cancel
          {:onClick on-close})
        (button/save
          {:onClick #(.save-business-info component)})))))

(defui AccountSettings
  static om/IQuery
  (query [_]
    [{:query/stripe-account [:stripe/id
                             :stripe/country
                             :stripe/verification
                             :stripe/legal-entity
                             :stripe/external-accounts
                             :stripe/default-currency
                             :stripe/payout-schedule
                             :stripe/details-submitted?]}
     {:proxy/verify (om/get-query verify/Verify)}
     ;{:proxy/payouts (om/get-query payouts/Payouts)}
     ;{:proxy/general (om/get-query general/General)}
     {:proxy/finances (om/get-query finances/StoreFinances)}
     {:query/auth [:user/email]}
     {:query/store [:db/id {:store/profile [:store.profile/email]}]}
     :query/current-route
     :query/messages])

  ;static store-common/IDashboardNavbarContent
  ;(has-subnav? [this current-route]
  ;  (debug "Has subnav...")
  ;  true)

  Object

  (save-legal-entity [this le]
    (debug "Save legal entity: " le)
    (let [validation (v/validate :field/legal-entity le form-inputs prefix-key)
          {:query/keys [store]} (om/props this)]
      (debug "Validation: " validation)
      (when (nil? validation)
        (let [{:field.legal-entity.address/keys [line1 postal city state] :as address} (:field.legal-entity/address le)]
          (msg/om-transact! this (cond-> [(list 'stripe/update-account {:account-params {:field/legal-entity le}
                                                                        :store-id       (:db/id store)})]
                                         ;(some? address)
                                         ;(conj (list 'store/update-shipping {:shipping {:shipping/address {:shipping.address/street   line1
                                         ;                                                                  :shipping.address/postal   postal
                                         ;                                                                  :shipping.address/locality city
                                         ;                                                                  :shipping.address/region   state}}
                                         ;                                    :store-id (:db/id store)}))
                                         :always
                                         (conj :query/stripe-account)))))
      (om/update-state! this assoc :input-validation validation)))
  (get-legal-entity [this]
    #?(:cljs
       (let [{:keys [entity-type]} (om/get-state this)
             street (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity.address/line1))
             postal (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity.address/postal))
             city (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity.address/city))
             state (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity.address/state))
             business-name (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity/business-name))
             ;entity-type (when (keyword? entity-type) (name entity-type))
             ]
         (f/remove-nil-keys
           {:field.legal-entity/address       {:field.legal-entity.address/line1  street
                                               :field.legal-entity.address/postal postal
                                               :field.legal-entity.address/city   city
                                               :field.legal-entity.address/state  state}
            :field.legal-entity/business-name business-name
            :field.legal-entity/type          entity-type})
         )))
  (save-business-info [this]
    #?(:cljs
       (let [{:keys [entity-type]} (om/get-state this)
             street (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity.address/line1))
             postal (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity.address/postal))
             city (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity.address/city))
             state (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity.address/state))
             business-name (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity/business-name))
             ;entity-type (when (keyword? entity-type) (name entity-type))

             input-map (.get-legal-entity this)]
         (mixpanel/track "Store: Save business information")
         (.save-legal-entity this input-map))))

  (save-personal-info [this]
    #?(:cljs
       (let [first-name (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity/first-name))
             last-name (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity/last-name))
             year (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity.dob/year))
             month (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity.dob/month))
             day (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity.dob/day))
             input-map {:field.legal-entity/first-name first-name
                        :field.legal-entity/last-name  last-name
                        :field.legal-entity/dob        {:field.legal-entity.dob/year  year
                                                        :field.legal-entity.dob/month month
                                                        :field.legal-entity.dob/day   day}}]
         (mixpanel/track "Store: Save personal information")
         (.save-legal-entity this input-map))))

  (save-email [this]
    #?(:cljs
       (let [email (utils/input-value-or-nil-by-id (:field.general/email form-inputs))
             validation (v/validate :field.general/email email form-inputs)
             {:query/keys [store]} (om/props this)]
         (debug "Validation: " validation)
         (if (nil? validation)
           (do
             (mixpanel/track "Store: Update contact email")
             (msg/om-transact! this [(list 'store/update-info {:db/id         (:db/id store)
                                                               :store/profile {:store.profile/email email}})
                                     :query/store])
             (om/update-state! this assoc :input-validation validation :edit/email? false))
           (om/update-state! this assoc :input-validation validation)))))

  (componentDidUpdate [this _ _]
    (let [last-message (msg/last-message this 'stripe/update-account)
          shipping-msg (msg/last-message this 'store/update-shipping)]
      (when (msg/final? last-message)
        (msg/clear-messages! this 'stripe/update-account)
        (if (msg/success? last-message)
          (let [address (:field.legal-entity/address (.get-legal-entity this))
                {:query/keys [store]} (om/props this)]
            (if (some? address)
              (let [{:field.legal-entity.address/keys [line1 postal city state]} address]
                (msg/om-transact! this [(list 'store/update-shipping {:shipping {:shipping/address {:shipping.address/street   line1
                                                                                                    :shipping.address/postal   postal
                                                                                                    :shipping.address/locality city
                                                                                                    :shipping.address/region   state}}
                                                                      :store-id (:db/id store)})]))
              (om/update-state! this dissoc :modal)))
          (om/update-state! this assoc :error-message (msg/message last-message))))
      (when (msg/final? shipping-msg)
        (msg/clear-messages! this 'store/update-shipping)
        (om/update-state! this dissoc :modal))))

  (initLocalState [_]
    {:active-tab :payouts})
  (render [this]
    (let [{:query/keys  [auth store stripe-account current-route]
           :proxy/keys  [finances]
           verify-props :proxy/verify} (om/props this)
          {:keys [modal]} (om/get-state this)
          {:stripe/keys [verification]} stripe-account
          accepted-tos? (not (some #(clojure.string/starts-with? % "tos_acceptance") (:stripe.verification/fields-needed verification)))
          {:keys [route route-params]} current-route
          last-message (msg/last-message this 'stripe/update-account)
          shipping-msg (msg/last-message this 'store/update-shipping)
          ]
      (debug "stripe account: " stripe-account)

      (debug "Finances: " finances)
      (dom/div
        {:id "sulo-account-settings"}
        (dom/div
          (css/add-class :section-title)
          (dom/h1 nil "Business"))
        (dom/div
          {:id "store-navbar"}
          (menu/horizontal
            nil
            (menu/item
              (when (= route :store-dashboard/business)
                (css/add-class :is-active))
              (dom/a {:href (routes/url :store-dashboard/business route-params)}
                     (dom/span nil "Business info")))
            (when (not-empty (:stripe.verification/fields-needed verification))
              (menu/item
                (when (= route :store-dashboard/business#verify)
                  (css/add-class :is-active))
                (dom/a {:href (routes/url :store-dashboard/business#verify route-params)}
                       (dom/span nil "Verify"))))))
        (when (or (msg/pending? last-message)
                  (msg/pending? shipping-msg))
          (common/loading-spinner nil (dom/span nil "Saving info...")))

        (cond (= modal :modal/edit-info)
              (edit-business-modal this)
              (= modal :modal/edit-personal)
              (edit-personal-modal this))


        (cond
          (= route :store-dashboard/business)
          (dom/div
            nil
            (dom/div
              (css/add-class :section-title)
              (dom/h2 nil "Account settings"))
            (callout/callout
              nil
              (let [legal-entity (:stripe/legal-entity stripe-account)
                    {:stripe.legal-entity/keys [address type first-name last-name business-name dob]} legal-entity
                    {:stripe.legal-entity.address/keys [line1 postal city state]} address]
                (debug "Legal entity: " legal-entity)
                (menu/vertical
                  (css/add-class :section-list)
                  (menu/item
                    nil
                    (grid/row
                      (->> (css/add-class :collapse)
                           (css/align :middle))
                      (grid/column
                        (grid/column-size {:small 12 :medium 6})
                        (cond (= type :company)
                              (dom/label nil "Company")
                              (= type :individual)
                              (dom/label nil "Individual business")
                              :else
                              (dom/label nil "Business details"))
                        (dom/p nil (dom/small nil "Edit your business details such as legal name, business type and address.")))
                      (grid/column
                        (css/text-align :right)
                        (if (some some? [business-name line1 city postal state])
                          (common/render-shipping {:shipping/name    (when (= type :company) business-name)
                                                   :shipping/address {:shipping.address/street   line1
                                                                      :shipping.address/postal   postal
                                                                      :shipping.address/locality city
                                                                      :shipping.address/region   state}}
                                                  nil)
                          ;(dom/p nil
                          ;
                          ;
                          ;       (when (= type :company)
                          ;         [(dom/span nil (str business-name))
                          ;          (dom/br nil)])
                          ;       (dom/small nil line1)
                          ;       (dom/br nil)
                          ;       (dom/small nil (str city ", " postal ", " state)))
                          (dom/p nil (dom/small nil (dom/i nil "No saved info"))))
                        (button/edit
                          {:onClick #(om/update-state! this assoc :modal :modal/edit-info)}
                          (dom/span nil "Edit business")))))
                  (menu/item
                    nil
                    (grid/row
                      (->> (css/add-class :collapse)
                           (css/align :middle))
                      (grid/column
                        (grid/column-size {:small 12 :medium 6})
                        (if (= type :company)
                          [(dom/label nil "Company representative")
                           (dom/p nil (dom/small nil "Your personal details as the representative of your company."))]
                          [(dom/label nil "Personal details")
                           (dom/p nil (dom/small nil "Your personal details as the owner of the business."))]))
                      (grid/column
                        (css/text-align :right)
                        (if (some some? [last-name first-name dob])
                          (dom/p nil
                                 (dom/span nil (str last-name ", " first-name))
                                 (dom/br nil)
                                 (let [{:stripe.legal-entity.dob/keys [year month day]} dob]
                                   (dom/small nil (dom/strong nil "DOB: ")
                                              (dom/span nil (date/date->string (str year "-" month "-" day))))))
                          (dom/p nil (dom/small nil (dom/i nil "No saved info"))))
                        (button/edit
                          {:onClick #(om/update-state! this assoc :modal :modal/edit-personal)}
                          (dom/span nil "Edit details"))))))))

            (dom/div
              (css/add-class :section-title)
              (dom/h2 nil "Public information"))
            (callout/callout
              nil
              (menu/vertical
                (css/add-class :section-list)
                (menu/item
                  nil
                  (grid/row
                    (->> (css/add-class :collapse)
                         (css/align :middle))
                    (grid/column
                      (grid/column-size {:small 12 :medium 6})
                      (dom/label nil "Contact email")
                      (dom/p nil (dom/small nil "This email will be visible to your customers and added as your contact information on their order receipts.")))
                    (let [email (or (get-in store [:store/profile :store.profile/email]) (:user/email auth))
                          {:keys [edit/email? input-validation]} (om/get-state this)]
                      (grid/column
                        (css/add-class :email-editor (css/text-align :right))
                        (v/input
                          (cond->> {:type         "text"
                                    :id           (:field.general/email form-inputs)
                                    :defaultValue email}
                                   (not email?)
                                   (css/add-class :uneditable))
                          input-validation)
                        (if email?
                          (dom/div
                            (css/add-class :action-buttons)
                            (button/cancel
                              {:onClick #(do (om/update-state! this assoc :edit/email? false :input-validation nil)
                                             #?(:cljs (set! (.-value (utils/element-by-id (:field.general/email form-inputs))) email)))})
                            (button/save
                              {:onClick #(.save-email this)}))
                          (button/edit
                            {:onClick #(om/update-state! this assoc :edit/email? true)}
                            (dom/span nil "Edit email"))))))))))

          (= route :store-dashboard/business#verify)

          [(dom/div
             (css/add-class :section-title)
             (dom/h2 nil "Verify account"))
           (callout/callout
             nil
             (verify/->Verify (om/computed verify-props
                                           {:stripe-account stripe-account})))])))))