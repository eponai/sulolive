(ns eponai.web.ui.store.finances.settings
  (:require
    [clojure.string :as string]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.dom :as dom]
    ;[eponai.common.ui.store.account.validate :as v]
    [eponai.web.ui.store.business.verify :as verify]
    [eponai.common.ui.elements.input-validate :as v]
    #?(:cljs
       [eponai.web.utils :as utils])
    #?(:cljs
       [eponai.web.ui.stripe :as stripe])
    [eponai.common.shared :as shared]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.client.parser.message :as msg]
    [eponai.common.ui.script-loader :as script-loader]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.web.ui.button :as button]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.table :as table]
    [eponai.common :as c]
    [eponai.common.mixpanel :as mixpanel]))
(def prefix-key "payouts-details-")

(def form-inputs verify/form-inputs)

(defn prefixed-id [k]
  (str prefix-key (get form-inputs k)))

(defn label-column [& content]
  (grid/column
    (grid/column-size {:small 12 :large 3})
    content))

(defn payout-schedule-info [interval week-anchor month-anchor delay-days]
  (let [capitalized (when week-anchor (string/capitalize week-anchor))]
    (cond (= interval "daily")
          (str "Every day, we'll bundle your transactions for the day and deposit them in your bank account " delay-days " days later.")

          (= interval "weekly")
          (str "Every week, all available payments will be deposited in your account on " capitalized ". (Your available balance will include payments made in the week prior to the previous " capitalized ".) ")

          (= interval "monthly")
          (str "Every month, all available payments will be deposited in your account on the " (c/ordinal-number month-anchor) " day of the month. (Your available balance will include payments made " delay-days " days before your scheduled transfer day.)"))))

(defn bank-account-modal [component]
  (let [on-close #(om/update-state! component dissoc :modal :modal-object :stripe-validation)
        {:keys [modal-object input-validation stripe-validation]} (om/get-state component)
        {:stripe.external-account/keys [currency country]} modal-object
        country (or country "CA")
        currency (or currency "CAD")]
    (common/modal
      {:on-close on-close
       :size     "tiny"}
      (dom/div
        nil
        (dom/h4 (css/add-class :header) "Your bank account")

        (dom/p nil (dom/small nil "Your bank account must be a checking account."))
        (dom/label nil "Currency")
        (dom/select
          (->> {:value    currency
                :disabled true
                :id       (prefixed-id :field.external-account/currency)}
               (css/add-class :currency))
          (dom/option {:value currency} currency))

        (dom/label nil "Bank country")
        (dom/select {:value    country
                     :disabled true
                     :id       (prefixed-id :field.external-account/country)}
                    (dom/option {:value country} country))

        (dom/label nil "Transit number")
        (v/input
          {:placeholder "12345"
           :type        "text"
           :id          (prefixed-id :field.external-account/transit-number)}
          input-validation)

        (dom/label nil "Institution number")
        (v/input
          {:placeholder "000"
           :type        "text"
           :id          (prefixed-id :field.external-account/institution-number)}
          input-validation)

        (dom/label nil "Account number")
        (v/input
          {:type "text"
           :id   (prefixed-id :field.external-account/account-number)}
          input-validation)

        (dom/div
          (css/add-class :error-message)
          (dom/p nil (dom/small nil (str stripe-validation))))
        (dom/div
          (css/add-class :action-buttons)
          (button/cancel
            {:onClick on-close})
          (button/save
            {:onClick #(do
                        (.update-bank-account component on-close))}))))))

(defn payout-schedule-modal [component]
  (let [{:keys                 [modal]
         :payout-schedule/keys [interval month-anchor week-anchor]} (om/get-state component)
        {:query/keys [stripe-account]} (om/props component)
        {:stripe/keys [payout-schedule]} stripe-account
        {:stripe.payout-schedule/keys [delay-days]} payout-schedule
        interval (or interval (:stripe.payout-schedule/interval payout-schedule) "daily")
        week-anchor (or week-anchor (:stripe.payout-schedule/week-anchor payout-schedule) "monday")
        month-anchor (or month-anchor (:stripe.payout-schedule/month-anchor payout-schedule) "1")]

    (common/modal
      {:on-close #(om/update-state! component dissoc :modal)
       :size     "tiny"}
      (dom/div
        nil
        (dom/h4 (css/add-class :header) "Change deposit schedule")
        (dom/p nil (dom/small nil (payout-schedule-info interval week-anchor month-anchor delay-days)))

        (dom/label nil "Deposit interval")
        (dom/select {:value    interval
                     :onChange #(om/update-state! component (fn [s]
                                                              (let [{:payout-schedule/keys [month-anchor week-anchor]} s
                                                                    interval (.. % -target -value)]
                                                                (cond-> (assoc s :payout-schedule/interval interval)
                                                                        (and (= interval "weekly")
                                                                             (nil? week-anchor))
                                                                        (assoc :payout-schedule/week-anchor "monday")
                                                                        (and (= interval "monthly")
                                                                             (nil? month-anchor))
                                                                        (assoc :payout-schedule/month-anchor "1")))))}
                    (dom/option {:value "daily"} "Daily")
                    (dom/option {:value "weekly"} "Weekly")
                    (dom/option {:value "monthly"} "Monthly"))
        (cond (= interval "weekly")
              [(dom/label nil "Deposit day")
               (dom/select {:value    week-anchor
                            :onChange #(om/update-state! component assoc :payout-schedule/week-anchor (.. % -target -value))}
                           (dom/option {:value "monday"} "Monday")
                           (dom/option {:value "tuesday"} "Tuesday")
                           (dom/option {:value "wednesday"} "Wednesday")
                           (dom/option {:value "thursday"} "Thursday")
                           (dom/option {:value "friday"} "Friday")
                           (dom/option {:value "saturday"} "Saturday")
                           (dom/option {:value "sunday"} "Sunday"))]
              (= interval "monthly")
              [(dom/label nil "Deposit day")
               (dom/select {:value    month-anchor
                            :onChange #(om/update-state! component assoc :payout-schedule/month-anchor (.. % -target -value))}
                           (map (fn [i]
                                  (let [day (inc i)]
                                    (dom/option {:value (str day)} (c/ordinal-number day))))
                                (range 31)))])
        (dom/div
          (css/add-class :action-buttons)
          (button/cancel
            {:onClick #(om/update-state! component dissoc :modal)})
          (button/save
            {:onClick #(do
                        (.update-payout-schedule component interval week-anchor month-anchor)
                        (om/update-state! component dissoc :modal))}))))))

(defn default-currency-modal [component]
  (let [{:keys [modal]} (om/get-state component)
        ;{:keys [stripe-account]} (om/get-computed component)
        {:query/keys [stripe-country-spec stripe-account]} (om/props component)
        {:country-spec/keys [supported-bank-account-currencies]} stripe-country-spec
        {:stripe/keys [default-currency external-accounts]} stripe-account
        currencies-with-account (map :stripe.external-account/currency external-accounts)]
    (debug "Currencies with accounts: " currencies-with-account)
    (common/modal
      {:on-close #(om/update-state! component dissoc :modal)
       :size     "tiny"}
      (dom/div
        nil
        (dom/h4 (css/add-class :header) "Change default currency")
        (dom/p nil (dom/small nil "Change the default currency for your account"))

        (dom/select
          (->> {:defaultValue default-currency
                :id           "sulo-default-currency-select"}
               (css/add-class :currency))
          (map (fn [c]
                 (let [code (key c)
                       is-disabled? (not (contains? (set currencies-with-account) (name code)))]
                   (dom/option
                     (cond-> {:value code}
                             is-disabled?
                             (assoc :disabled true))
                     (if is-disabled?
                       (str (string/upper-case (name code)) " (no bank account)")
                       (string/upper-case (name code))))))
               supported-bank-account-currencies))

        (dom/div
          (css/add-class :action-buttons)
          (button/cancel
            {:onClick #(om/update-state! component dissoc :modal)})
          (button/save
            {:onClick #(do
                        (.update-default-currency component)
                        (om/update-state! component dissoc :modal))}))))))

(defui Payouts-no-loader
  static om/IQuery
  (query [_]
    [:query/messages
     :query/stripe-country-spec
     {:query/stripe-account [:stripe/id
                             :stripe/external-accounts
                             :stripe/default-currency
                             :stripe/payout-schedule]}
     {:query/store [:db/id]}])
  Object
  (update-default-currency [this]
    #?(:cljs
       (let [{:query/keys [store]} (om/props this)]
         (let [currency (utils/selected-value-by-id "sulo-default-currency-select")]
           (msg/om-transact! this [(list 'stripe/update-account
                                         {:account-params {:field/default-currency currency}
                                          :store-id       (:db/id store)})
                                   :query/stripe-account])))))

  (update-bank-account [this on-close]
    #?(:cljs
       (let [{:query/keys [store]} (om/props this)
             currency (utils/input-value-by-id (prefixed-id :field.external-account/currency))
             country (utils/input-value-by-id (prefixed-id :field.external-account/country))
             transit (utils/input-value-by-id (prefixed-id :field.external-account/transit-number))
             institution (utils/input-value-by-id (prefixed-id :field.external-account/institution-number))
             account (utils/input-value-by-id (prefixed-id :field.external-account/account-number))

             input-map {:field/external-account {:field.external-account/account-number     account
                                                 :field.external-account/currency           currency
                                                 :field.external-account/country            country
                                                 :field.external-account/institution-number institution
                                                 :field.external-account/transit-number     transit}}
             validation (v/validate :account/activate input-map form-inputs prefix-key)]

         (when (nil? validation)
           (stripe/bank-account (shared/by-key this :shared/stripe)
                                {:country        country
                                 :currency       currency
                                 :routing_number (str transit institution)
                                 :account_number account}
                                {:on-success (fn [{:keys [token]}]
                                               (mixpanel/track "Store: Update bank account")
                                               (msg/om-transact! this [(list 'stripe/update-account
                                                                             {:account-params {:field/external-account token}
                                                                              :store-id       (:db/id store)})
                                                                       :query/stripe-account])
                                               (on-close))
                                 :on-error   (fn [error-message]
                                               (om/update-state! this assoc :stripe-validation error-message))}))
         (om/update-state! this assoc :input-validation validation))))

  (update-payout-schedule [this interval week-anchor month-anchor]
    #?(:cljs
       (let [{:query/keys [store]} (om/props this)
             schedule (cond-> {:field.payout-schedule/interval interval}
                              (= interval "weekly")
                              (assoc :field.payout-schedule/week-anchor week-anchor)
                              (= interval "monthly")
                              (assoc :field.payout-schedule/month-anchor month-anchor))]
         (mixpanel/track "Store: Update deposit schedule")
         (msg/om-transact! this [(list 'stripe/update-account
                                       {:account-params {:field/payout-schedule schedule}
                                        :store-id       (:db/id store)})
                                 :query/stripe-account]))))
  (render [this]
    (let [{:keys                 [modal modal-object input-validation stripe-validation]
           :payout-schedule/keys [interval month-anchor week-anchor]} (om/get-state this)
          {:query/keys [stripe-account]} (om/props this)
          {:stripe/keys [external-accounts default-currency payout-schedule]} stripe-account
          {:stripe.payout-schedule/keys [delay-days]} payout-schedule
          interval (or interval (:stripe.payout-schedule/interval payout-schedule) "daily")
          week-anchor (or week-anchor (:stripe.payout-schedule/week-anchor payout-schedule) "monday")
          month-anchor (or month-anchor (:stripe.payout-schedule/month-anchor payout-schedule) "1")
          message (msg/last-message this 'stripe/update-account)]
      (debug "Stripe account: " stripe-account)
      (dom/div
        {:id "sulo-finances-settings"}
        (cond (msg/pending? message)
              (common/loading-spinner nil)
              (msg/final? message)
              (when-not (msg/success? message)
                (dom/div
                  (->> (css/callout)
                       (css/add-class :notification)
                       (css/add-class ::css/color-alert))
                  (dom/p nil (msg/message message)))))

        (dom/div
          (css/show-for-sr (css/add-class :section-title))
          (dom/h1 nil "Deposit settings"))

        (cond (= modal :default-currency)
              (default-currency-modal this)
              (= modal :payout-schedule)
              (payout-schedule-modal this)
              (= modal :modal/bank-account)
              (bank-account-modal this))


        (dom/div
          (css/add-class :section-title)
          (dom/h2 nil "Deposit settings"))

        (callout/callout
          nil
          (menu/vertical
            (css/add-class :section-list)
            (let [{:stripe.payout-schedule/keys [interval month-anchor week-anchor]} payout-schedule
                  anchor (cond (= interval "monthly")
                               month-anchor
                               (= interval "weekly")
                               (string/capitalize week-anchor))]
              (menu/item
                nil
                (grid/row
                  (->> (css/add-class :collapse)
                       (css/align :middle))
                  (grid/column
                    (grid/column-size {:small 12 :medium 6})
                    (dom/label nil "Deposit schedule")
                    (dom/p nil (dom/small nil (payout-schedule-info interval week-anchor month-anchor delay-days))))
                  (grid/column
                    (css/text-align :right)
                    (dom/p nil (dom/span nil (str (string/capitalize (or interval ""))))
                           (when anchor
                             (dom/span nil (str " (" (c/ordinal-number anchor) ")")))
                           (dom/br nil)
                           (dom/small nil (str delay-days " day rolling basis")))
                    (button/store-setting-default
                      {:onClick #(om/update-state! this assoc :modal :payout-schedule)}
                      (dom/span nil "Change schedule"))))))
            ;(menu/item
            ;  nil
            ;  (grid/row
            ;    (->> (css/add-class :collapse)
            ;         (css/align :middle))
            ;    (grid/column
            ;      (grid/column-size {:small 12 :medium 6})
            ;      (dom/label nil "Default currency")
            ;      (dom/p nil (dom/small nil "Charges for a currency without a bank account will be converted to your default currency, ")))
            ;    (grid/column
            ;      (css/text-align :right)
            ;      (dom/p nil (dom/strong nil (string/upper-case (or default-currency ""))))
            ;      (button/user-setting-default
            ;        {:onClick #(om/update-state! this assoc :modal :default-currency)}
            ;        (dom/span nil "Change currency")))))
            ))
        ;(callout/callout
        ;  nil
        ;  (default-currency-section this))

        (dom/div
          (css/add-class :section-title)
          (dom/h2 nil "Bank accounts"))


        ;(menu/vertical
        ;  (css/add-classes [:section-list :section-list--bank-accounts]))
        (if (not-empty external-accounts)
          [
           (callout/callout
             (css/add-class :bank-accounts-container)
             (menu/vertical
               (->> (css/add-classes [:bank-list :section-list]) (css/hide-for :medium))
               (map
                 (fn [bank-acc]
                   (let [{:stripe.external-account/keys [bank-name currency last4 country default-for-currency?]} bank-acc]
                     (menu/item
                       (css/add-classes [:bank-account])
                       (dom/div
                         nil
                         (dom/p (css/add-class :bank-detail--account)
                                ;(dom/i {:classes ["fa fa-bank -fa-fw"]})
                                (dom/span nil bank-name)
                                (dom/small nil (str "•••• " last4)))
                         (dom/p (css/add-class :bank-detail--location)
                                (dom/strong (css/add-class :currency) currency)
                                (dom/span (css/add-class :country) (str "(" country ")"))))

                       (dom/div
                         (css/text-align :right)
                         ;(when-not (and default-for-currency?
                         ;               (= currency default-currency))
                         ;  (button/button
                         ;    (->> {:onClick #(om/update-state! this assoc :modal :delete-account)}
                         ;         (css/add-classes [:small :alert :hollow]))
                         ;    (dom/span nil "Delete")))
                         (button/edit
                           {:onClick #(om/update-state! this assoc :modal :modal/bank-account :modal-object bank-acc)}
                           (dom/span nil "Edit"))))))
                 external-accounts)))
           (table/table
             (css/show-for :medium)
             (table/tbody
               nil
               (map
                 (fn [bank-acc]
                   (let [{:stripe.external-account/keys [bank-name currency last4 country default-for-currency?]} bank-acc]
                     (table/tbody-row
                       nil
                       ;(table/td
                       ;  (css/add-class :bank-detail--icon)
                       ;  (dom/i {:classes ["fa fa-bank -fa-fw"]}))
                       (table/td
                         (css/add-class :bank-detail--location)
                         (dom/p nil
                                (dom/strong (css/add-class :currency) currency)
                                (dom/span (css/add-class :country) (str "(" country ")")))
                         ;(dom/div {:classes ["bank-detail currency"]}
                         ;         (dom/span nil currency))
                         ;(dom/div {:classes ["bank-detail country"]}
                         ;         (dom/span nil country))
                         )
                       (table/td
                         (css/add-class :bank-detail--account)
                         (dom/div
                           {:classes [:bank-account]})
                         (dom/p nil
                                (dom/span nil bank-name)
                                (dom/small nil (str "•••• " last4))))
                       (table/td
                         (css/text-align :right)
                         ;(when-not (and default-for-currency?
                         ;               (= currency default-currency))
                         ;  (button/button
                         ;    (->> {:onClick #(om/update-state! this assoc :modal :delete-account)}
                         ;         (css/add-classes [:small :alert :hollow]))
                         ;    (dom/span nil "Delete")))
                         (button/edit
                           {:onClick #(om/update-state! this assoc :modal :modal/bank-account :modal-object bank-acc)}
                           (dom/span nil "Edit"))

                         ))))
                 external-accounts)))]
          (grid/row
            nil
            (grid/column
              nil
              (button/button-small
                {:onClick #(om/update-state! this assoc :modal :modal/bank-account :modal-object nil)}
                (dom/span nil "Add bank account")))))
        ;(payout-schedule this)

        ;(when (< 1 (count external-accounts)))

        ))))

(def FinancesSettings (script-loader/stripe-loader Payouts-no-loader))

(def ->FinancesSettings (om/factory FinancesSettings))