(ns eponai.web.ui.store.business
  (:require
    [eponai.client.routes :as routes]
    [eponai.client.parser.message :as msg]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.web.ui.store.common :as store-common]
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
    [eponai.common.format :as f]))

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
        (button/user-setting-default
          {:onClick on-close}
          (dom/span nil "Cancel"))
        (button/user-setting-cta
          {:onClick #(.save-personal-info component)}
          (dom/span nil "Save"))))))

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
        (button/user-setting-default
          {:onClick on-close}
          (dom/span nil "Cancel"))
        (button/user-setting-cta
          {:onClick #(.save-business-info component)}
          (dom/span nil "Save"))))))

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
     :query/current-route
     :query/messages])

  static store-common/IDashboardNavbarContent
  (has-subnav? [this current-route]
    (debug "Has subnav...")
    true)

  Object

  (save-legal-entity [this le]
    (debug "Save legal entity: " le)
    (let [validation (v/validate :field/legal-entity le form-inputs prefix-key)
          {:keys [store]} (om/get-computed this)]
      (debug "Validation: " validation)
      (when (nil? validation)
        (msg/om-transact! this [(list 'stripe/update-account {:account-params {:field/legal-entity le}
                                                              :store-id       (:db/id store)})
                                :query/stripe-account]))
      (om/update-state! this assoc :input-validation validation)))
  (save-business-info [this]
    #?(:cljs
       (let [{:keys [entity-type]} (om/get-state this)
             street (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity.address/line1))
             postal (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity.address/postal))
             city (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity.address/city))
             state (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity.address/state))
             business-name (utils/input-value-or-nil-by-id (prefixed-id :field.legal-entity/business-name))
             ;entity-type (when (keyword? entity-type) (name entity-type))

             input-map (f/remove-nil-keys
                         {:field.legal-entity/address       {:field.legal-entity.address/line1  street
                                                             :field.legal-entity.address/postal postal
                                                             :field.legal-entity.address/city   city
                                                             :field.legal-entity.address/state  state}
                          :field.legal-entity/business-name business-name
                          :field.legal-entity/type          entity-type})]
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
         (.save-legal-entity this input-map))))

  (componentDidUpdate [this _ _]
    (let [last-message (msg/last-message this 'stripe/update-account)]
      (when (msg/final? last-message)
        (msg/clear-messages! this 'stripe/update-account)
        (if (msg/success? last-message)
          (om/update-state! this dissoc :modal)
          (om/update-state! this assoc :error-message (msg/message last-message))))))

  (initLocalState [_]
    {:active-tab :payouts})
  (render [this]
    (let [{:query/keys [stripe-account current-route]
           :proxy/keys [finances]
           verify-props :proxy/verify} (om/props this)
          {:keys [modal]} (om/get-state this)
          {:stripe/keys [verification]} stripe-account
          accepted-tos? (not (some #(clojure.string/starts-with? % "tos_acceptance") (:stripe.verification/fields-needed verification)))
          {:keys [route route-params]} current-route
          last-message (msg/last-message this 'stripe/update-account)
          ]

      (debug "Finances: " finances)
      (dom/div
        {:id "sulo-account-settings"}
        (dom/div
          (->> {:id "store-navbar"}
               (css/add-class :navbar-container))
          (dom/nav
            (->> (css/add-class :navbar)
                 (css/add-class :top-bar))
            (menu/horizontal
              (css/align :center)
              (menu/item
                (when (= route :store-dashboard/business#verify)
                  (css/add-class :is-active))
                (dom/a {:href (routes/url :store-dashboard/business#verify route-params)}
                       (dom/span nil "Verify")))
              (menu/item
                (when (= route :store-dashboard/business)
                  (css/add-class :is-active))
                (dom/a {:href (routes/url :store-dashboard/business route-params)}
                       (dom/span nil "Business info")))
              )
            ))
        (when (msg/pending? last-message)
          (common/loading-spinner nil (dom/span nil "Saving info...")))

        (cond (= modal :modal/edit-info)
              (edit-business-modal this)
              (= modal :modal/edit-personal)
              (edit-personal-modal this))
        ;(dom/div
        ;  (css/add-class :section-title))
        (dom/h1 (css/show-for-sr) "Business")

        ;(callout/callout
        ;  nil)


        (cond

          ;(= route :store-dashboard/settings#payouts)
          ;(finances/->StoreFinances finances)

          (= route :store-dashboard/business)
          (dom/div
            nil
            ;(when-let [needs-verification? (or (not accepted-tos?)
            ;                                 (and (not-empty (get-in stripe-account [:stripe/verification :stripe.verification/fields-needed]))
            ;                                      (some? (:stripe.verification/due-by verification))))]
            ;  [(dom/div
            ;     (css/add-class :section-title)
            ;     (dom/h2 nil "Verify account"))
            ;   (callout/callout
            ;     nil
            ;     (verify/->Verify (om/computed verify
            ;                                   {:store          store
            ;
            ;                            :stripe-account stripe-account})))])

            (dom/div
              (css/add-class :section-title)
              (dom/h2 nil "Account settings"))
            (callout/callout
              nil
              (let [
                    ;{:keys [store]} (om/get-computed thi)
                    ;{:business/keys [input-validation]} (om/get-state component)
                    ;{:query/keys [stripe-account]} (om/props component)
                    legal-entity (:stripe/legal-entity stripe-account)
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
                          (dom/p nil

                                 (when (= type :company)
                                   [(dom/span nil (str business-name))
                                    (dom/br nil)]
                                   ;[(dom/span nil (str last-name ", " first-name))
                                   ; (dom/br nil)]
                                   )
                                 (dom/small nil line1)
                                 (dom/br nil)
                                 (dom/small nil (str city ", " postal ", " state))
                                 ;(when anchor
                                 ;  (dom/span nil (str " (" (c/ordinal-number anchor) ")")))

                                 )
                          (dom/p nil (dom/small nil (dom/i nil "No saved info"))))
                        (button/user-setting-default
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
                                              (dom/span nil (date/date->string (str year "-" month "-" day)))))

                                 ;(dom/small nil line1)
                                 ;(dom/br nil)
                                 ;(dom/small nil (str city ", " postal ", " state))
                                 ;(when anchor
                                 ;  (dom/span nil (str " (" (c/ordinal-number anchor) ")")))

                                 )
                          (dom/p nil (dom/small nil (dom/i nil "No saved info"))))
                        (button/user-setting-default
                          {:onClick #(om/update-state! this assoc :modal :modal/edit-personal)}
                          (dom/span nil "Edit details")))))
                  )))

            ;(dom/div
            ;  (css/add-class :section-title)
            ;  (dom/h2 nil "Public information"))
            ;(callout/callout
            ;  nil
            ;  (menu/vertical
            ;    (css/add-class :section-list)
            ;    (menu/item
            ;      nil
            ;      (grid/row
            ;        (->> (css/add-class :collapse)
            ;             (css/align :middle))
            ;        (grid/column
            ;          (grid/column-size {:small 12 :medium 6})
            ;          (dom/label nil "Your email")
            ;          (dom/p nil (dom/small nil "Email shown to your customers where they can contact you.")))
            ;        (grid/column
            ;          (css/text-align :right)
            ;          (dom/p nil (dom/span nil "dev @sulo.live"))
            ;          (button/user-setting-default
            ;            {:onClick #(om/update-state! this assoc :modal :modal/edit-info)}
            ;            (dom/span nil "Edit")))))))
            )

          (= route :store-dashboard/business#verify)

          [(dom/div
             (css/add-class :section-title)
             (dom/h2 nil "Verify account"))
           (callout/callout
             nil
             (verify/->Verify (om/computed verify-props
                                           {:stripe-account stripe-account})))])))))