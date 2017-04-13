(ns eponai.common.ui.store.account.payouts
  (:require
    [clojure.string :as string]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.store.account.validate :as v]
    [eponai.common.ui.elements.input-validate :as validate]
    #?(:cljs
       [eponai.web.utils :as utils])
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.client.parser.message :as msg]))

(def prefix-key "payouts-details-")

(def stripe-key "pk_test_VhkTdX6J9LXMyp5nqIqUTemM")

(defn prefixed-id [k]
  (str prefix-key (get v/form-inputs k)))

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
          (str "Every month, all available payments will be deposited in your account on the " month-anchor " day of the month. (Your available balance will include payments made " delay-days " days before your scheduled transfer day.)"))))

(defn payout-schedule [component]
  (let [{:keys                 [modal]
         :payout-schedule/keys [interval month-anchor week-anchor]} (om/get-state component)
        {:keys [stripe-account]} (om/get-computed component)
        {:stripe/keys [payout-schedule]} stripe-account
        {:stripe.payout-schedule/keys [delay-days]} payout-schedule
        interval (or interval (:stripe.payout-schedule/interval payout-schedule) "daily")
        week-anchor (or week-anchor (:stripe.payout-schedule/week-anchor payout-schedule) "monday")
        month-anchor (or month-anchor (:stripe.payout-schedule/month-anchor payout-schedule) "1")]
    (dom/div
      nil
      (grid/row
        (->> (css/align :top))
        (grid/column
          (grid/column-size {:small 12 :large 2})
          (dom/label nil "Payout schedule"))

        (grid/column
          nil
          (grid/row-column
            (css/add-class :payout-schedule-container)
            (grid/row
              (css/align :middle)
              (grid/column
                nil
                (dom/div
                  (css/add-class :payout-schedule)
                  (let [{:stripe.payout-schedule/keys [interval month-anchor week-anchor]} payout-schedule
                        anchor (cond (= interval "monthly")
                                     month-anchor
                                     (= interval "weekly")
                                     (string/capitalize week-anchor))]
                    (dom/span nil (str (string/capitalize (or interval ""))
                                       (when anchor
                                         (str " (" anchor ")"))
                                       " - " delay-days " day rolling basis")))))

              (grid/column
                (css/add-class :shrink)
                (dom/a
                  (->> {:onClick #(om/update-state! component assoc :modal :payout-schedule)}
                       (css/button-hollow)
                       (css/add-class :small))

                  (dom/span nil "Change schedule"))
                ))))
        (when (= modal :payout-schedule)
          (common/modal
            {:on-close #(om/update-state! component dissoc :modal)}
            (dom/div
              nil
              (dom/h4 (css/add-class :header) "Change payout schedule")
              (dom/p nil (dom/small nil (payout-schedule-info interval week-anchor month-anchor delay-days)))
              (dom/div
                (css/callout)
                (dom/p (css/add-class :header)))
              (grid/row
                nil
                (label-column
                  nil
                  (dom/label nil "Payout interval"))
                (grid/column
                  nil
                  (dom/select {:defaultValue interval
                               :onChange     #(om/update-state! component (fn [s]
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
                              (dom/option {:value "monthly"} "Monthly"))))
              (cond (= interval "weekly")
                    (grid/row
                      nil
                      (label-column
                        nil
                        (dom/label nil "Payout day"))
                      (grid/column
                        nil
                        (dom/select {:defaultValue week-anchor
                                     :onChange     #(om/update-state! component assoc :payout-schedule/week-anchor (.. % -target -value))}
                                    (dom/option {:value "monday"} "Monday")
                                    (dom/option {:value "tuesday"} "Tuesday")
                                    (dom/option {:value "wednesday"} "Wednesday")
                                    (dom/option {:value "thursday"} "Thursday")
                                    (dom/option {:value "friday"} "Friday")
                                    (dom/option {:value "saturday"} "Saturday")
                                    (dom/option {:value "sunday"} "Sunday"))))
                    (= interval "monthly")
                    (grid/row
                      nil
                      (label-column
                        nil
                        (dom/label nil "Payout day"))
                      (grid/column
                        nil
                        (dom/select {:defaultValue month-anchor
                                     :onChange     #(om/update-state! component assoc :payout-schedule/month-anchor (.. % -target -value))}
                                    (map (fn [i]
                                           (let [day (str (inc i))]
                                             (dom/option {:value day} day)))
                                         (range 31))))))
              (dom/div
                (css/callout (css/text-align :right))
                (dom/p (css/add-class :header))
                (dom/a (->> {:onClick #(om/update-state! component dissoc :modal)}
                            (css/button-hollow)) (dom/span nil "Cancel"))
                (dom/a
                  (->> {:onClick #(do
                                   (.update-payout-schedule component interval week-anchor month-anchor)
                                   (om/update-state! component dissoc :modal))}
                       (css/button)) (dom/span nil "Save"))))))))))

(defn default-currency-section [component]
  (let [{:keys [modal]} (om/get-state component)
        {:keys [stripe-account]} (om/get-computed component)
        {:query/keys [stripe-country-spec]} (om/props component)
        {:country-spec/keys [supported-bank-account-currencies]} stripe-country-spec
        {:stripe/keys [default-currency]} stripe-account]
    (debug "Country spec: " stripe-country-spec)
    (dom/div
      nil
      (grid/row
        (->> (css/align :top))
        (grid/column
          (grid/column-size {:small 12 :large 2})
          (dom/label nil "Default currency"))

        (grid/column
          nil
          (grid/row-column
            (css/add-class :payout-schedule-container)
            (grid/row
              (css/align :middle)
              (grid/column
                nil
                (dom/div
                  (->> (css/add-class :currency)
                       (css/add-class :default-currency))
                  (dom/span (->> (css/add-class :label)
                                 (css/add-class ::css/color-secondary))
                            default-currency)))

              (grid/column
                (css/add-class :shrink)
                (dom/a
                  (->> {:onClick #(om/update-state! component assoc :modal :default-currency)}
                       (css/button-hollow)
                       (css/add-class :small))

                  (dom/span nil "Change default currency"))
                ))))
        (when (= modal :default-currency)
          (common/modal
            {:on-close #(om/update-state! component dissoc :modal)}
            (dom/div
              nil
              (dom/h4 (css/add-class :header) "Change default currency")
              (dom/p nil (dom/small nil "Change the default currency for your account"))

              (dom/select
                (->> {:defaultValue default-currency
                      :id           "sulo-default-currency-select"}
                     (css/add-class :currency))
                (map (fn [c]
                       (let [code (key c)]
                         (dom/option
                           {:value code} code)))
                     supported-bank-account-currencies))
              (dom/div
                (css/callout)
                (dom/p (css/add-class :header))
                (dom/div
                  (css/text-align :right)
                  (dom/a (->> {:onClick #(om/update-state! component dissoc :modal)}
                              (css/button-hollow)) (dom/span nil "Cancel"))
                  (dom/a
                    (->> {:onClick #(do
                                     (.update-default-currency component)
                                     (om/update-state! component dissoc :modal))}
                         (css/button)) (dom/span nil "Save")))))))))))

(defui Payouts
  static om/IQuery
  (query [_]
    [:query/messages
     :query/stripe-country-spec])
  Object
  (update-default-currency [this]
    #?(:cljs
       (let [{:keys [store]} (om/get-computed this)]
         (let [currency (utils/selected-value-by-id "sulo-default-currency-select")]
           (msg/om-transact! this `[(stripe/update-account ~{:account-params {:field/default-currency currency}
                                                             :store-id       (:db/id store)})
                                    :query/stripe-account])))))

  (update-bank-account [this on-close]
    #?(:cljs
       (let [{:keys [store]} (om/get-computed this)
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
             validation (v/validate :account/activate input-map prefix-key)]
         (debug "Account: " input-map)
         (debug "Validation: " validation)
         (when (nil? validation)
           (.setPublishableKey js/Stripe stripe-key)
           (debug "Stripe token params; " {:country        country
                                           :currency       currency
                                           :routing_number (str transit institution)
                                           :account_number account})
           (.createToken js/Stripe.bankAccount
                         #js {:country        country
                              :currency       currency
                              :routing_number (str transit institution)
                              :account_number account}
                         (fn [status response]
                           (debug "Stripe token: " response)
                           (when (= status 200)
                             (let [token (.-id response)]
                               (msg/om-transact! this `[(stripe/update-account
                                                          ~{:account-params {:field/external-account token}
                                                            :store-id       (:db/id store)})
                                                        :query/stripe-account]))
                             (on-close)))))
         (om/update-state! this assoc :input-validation validation))))

  (update-payout-schedule [this interval week-anchor month-anchor]
    #?(:cljs
       (let [{:keys [store]} (om/get-computed this)
             schedule (cond-> {:field.payout-schedule/interval     interval}
                              (= interval "weekly")
                              (assoc :field.payout-schedule/week-anchor  week-anchor)
                              (= interval "monthly")
                              (assoc :field.payout-schedule/month-anchor month-anchor))]
         (msg/om-transact! this `[(stripe/update-account
                                    ~{:account-params {:field/payout-schedule schedule}
                                      :store-id (:db/id store)})
                                  :query/stripe-account]))))
  (render [this]
    (let [{:keys [modal modal-object input-validation]} (om/get-state this)
          {:keys [stripe-account]} (om/get-computed this)
          {:stripe/keys [external-accounts default-currency]} stripe-account
          message (msg/last-message this 'stripe/update-account)]

      (dom/div
        nil
        (cond (msg/pending? message)
              (common/loading-spinner)
              (msg/final? message)
              (when-not (msg/success? message)
                (dom/div
                  (->> (css/callout)
                       (css/add-class :notification)
                       (css/add-class ::css/color-alert))
                  (dom/p nil (msg/message message)))))
        (dom/div
          (css/callout)
          (dom/p (css/add-class :header) "Payouts")
          (payout-schedule this))

        (when (< 1 (count external-accounts))
          (dom/div
            (css/callout)
            (default-currency-section this)))
        (dom/div
          (css/callout)
          (dom/p (css/add-class :header))
          (grid/row
            (css/add-class :external-account-list)
            (grid/column
              (grid/column-size {:small 12 :large 2})
              (dom/label nil "Bank Account"))
            (grid/column
              nil
              (if (not-empty external-accounts)
                (map
                  (fn [bank-acc]
                    (let [{:stripe.external-account/keys [bank-name currency last4 country default-for-currency?]} bank-acc]
                      (grid/row-column
                        {:classes [:bank-account-container]}
                        (grid/row
                          (css/align :middle)
                          (grid/column
                            nil
                            (dom/div
                              {:classes [:bank-account]}
                              (dom/div {:classes ["bank-detail icon"]}
                                       (dom/i {:classes ["fa fa-bank -fa-fw"]}))
                              (dom/div {:classes ["bank-detail bank-name"]}
                                       (dom/span nil bank-name))
                              (dom/div {:classes ["bank-detail account"]}
                                       (dom/small nil (str "•••• " last4)))
                              (dom/div {:classes ["bank-detail currency"]}
                                       (dom/span (->> (css/add-class :label) (css/add-class ::css/color-secondary)) currency))
                              ;(dom/div {:classes ["bank-detail"]}
                              ;         (dom/span nil "/"))
                              ;(dom/div {:classes ["bank-detail routing"]}
                              ;         (dom/span nil routing-number))

                              (dom/div {:classes ["bank-detail country"]}
                                       (dom/span nil country))))
                          (grid/column
                            (css/add-class :shrink)
                            (when-not (and default-for-currency?
                                           (= currency default-currency))
                              (dom/a
                                (->> {:onClick #(om/update-state! this assoc :modal :delete-account)}
                                     (css/button-hollow)
                                     (css/add-class :small)
                                     (css/add-class ::css/color-alert)) (dom/span nil "Delete")))
                            (dom/a
                              (->> {:onClick #(om/update-state! this assoc :modal :bank-account :modal-object bank-acc)}
                                   (css/button-hollow)
                                   (css/add-class :small)) (dom/span nil "Edit"))
                            )))))
                  external-accounts)
                (dom/a
                  (css/button-hollow)
                  (dom/span nil "Add bank account..."))))
            (when (= modal :bank-account)
              (let [on-close #(om/update-state! this dissoc :modal :modal-object)
                    {:stripe.external-account/keys [bank-name currency last4 country default-for-currency?]} modal-object]
                (common/modal
                  {:on-close on-close}
                  (dom/div
                    nil
                    (dom/h4 (css/add-class :header) "Your bank account")
                    (dom/div (css/callout)
                             (dom/p nil (dom/small nil "Your bank account must be a checking account."))
                             (dom/p (css/add-class :header))
                             (grid/row-column
                               nil
                               (grid/row
                                 nil
                                 (grid/column
                                   (grid/column-size {:small 12 :large 3})
                                   (dom/label nil "Currency"))
                                 (grid/column
                                   nil
                                   (dom/select
                                     (->> {:value    currency
                                           :disabled true
                                           :id       (prefixed-id :field.external-account/currency)}
                                          (css/add-class :currency))
                                     (dom/option {:value currency} currency))))

                               (grid/row
                                 nil
                                 (grid/column
                                   (grid/column-size {:small 12 :large 3})
                                   (dom/label nil "Bank country"))
                                 (grid/column
                                   nil
                                   (dom/select {:value    country
                                                :disabled true
                                                :id       (prefixed-id :field.external-account/country)}
                                               (dom/option {:value country} country)))))

                             (grid/row-column
                               nil
                               (grid/row
                                 nil
                                 (grid/column
                                   (grid/column-size {:small 12 :large 3})
                                   (dom/label nil "Transit number"))
                                 (grid/column
                                   nil
                                   (validate/input
                                     {:placeholder "12345"
                                      :type        "text"
                                      :id          (prefixed-id :field.external-account/transit-number)}
                                     input-validation)))
                               (grid/row
                                 nil
                                 (grid/column
                                   (grid/column-size {:small 12 :large 3})
                                   (dom/label nil "Institution number"))
                                 (grid/column
                                   nil
                                   (validate/input
                                     {:placeholder "000"
                                      :type        "text"
                                      :id          (prefixed-id :field.external-account/institution-number)}
                                     input-validation)))
                               (grid/row
                                 nil
                                 (grid/column
                                   (grid/column-size {:small 12 :large 3})
                                   (dom/label nil "Account number"))
                                 (grid/column
                                   nil
                                   (validate/input
                                     {:type "text"
                                      :id   (prefixed-id :field.external-account/account-number)}
                                     input-validation)))))



                    (dom/div
                      (css/callout (css/text-align :right))
                      (dom/p (css/add-class :header))
                      (dom/a (->> {:onClick on-close}
                                  (css/button-hollow)) (dom/span nil "Cancel"))
                      (dom/a
                        (->> {:onClick #(do
                                         (.update-bank-account this on-close))}
                             (css/button)) (dom/span nil "Save")))))))))

        (dom/div
          (css/callout)
          (dom/p (css/add-class :header))
          ;(dom/div
          ;  (css/text-align :right)
          ;  (dom/a (css/button) (dom/span nil "Save")))
          )
        ))))

(def ->Payouts (om/factory Payouts))