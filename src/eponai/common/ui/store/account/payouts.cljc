(ns eponai.common.ui.store.account.payouts
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.dom :as dom]
    #?(:cljs
       [eponai.web.utils :as utils])
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.client.parser.message :as msg]))

(defn payout-schedule [component]
  (let [{:keys [modal]} (om/get-state component)]
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
                  (dom/span nil "Daily - 7 day rolling basis")))
              ;(dom/input
              ;  {:disabled true
              ;   :type "text"
              ;   :value "Daily - 7 day rolling basis"})
              ;(dom/select {:defaultValue "daily"}
              ;            (dom/option {:value "daily"}
              ;                        (dom/span nil "Daily"))
              ;            (dom/option {:value "weekly"}
              ;                        (dom/strong nil "Weekly"))
              ;            (dom/option {:value "monthly"}
              ;                        (dom/strong nil "Monthly")))

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
              (dom/p nil (dom/small nil "Every day, we'll bundle your transactions for the day and deposit them in your bank account 7 days later. The very first payout Stripe makes to your bank can take up to 10 days to post outside of the US or Canada."))
              (dom/select {:defaultValue "daily"}
                          (dom/option {:value "daily"} "Daily")
                          (dom/option {:value "weekly"} "Weekly")
                          (dom/option {:value "monthly"} "Monthly"))
              (dom/div
                (css/callout (css/text-align :right))
                (dom/p (css/add-class :header))
                (dom/a (->> {:onClick #(om/update-state! component dissoc :modal)}
                            (css/button-hollow)) (dom/span nil "Cancel"))
                (dom/a
                  (->> {:onClick #(om/update-state! component dissoc :modal)}
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
                      :id "sulo-default-currency-select"}
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
  (render [this]
    (let [{:keys [modal]} (om/get-state this)
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

        (when (> 1 (count external-accounts))
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
                              (->> {:onClick #(om/update-state! this assoc :modal :bank-account)}
                                   (css/button-hollow)
                                   (css/add-class :small)) (dom/span nil "Edit"))
                            )))))
                  external-accounts)
                (dom/a
                  (css/button-hollow)
                  (dom/span nil "Add bank account..."))))
            (when (= modal :bank-account)
              (common/modal
                {:on-close #(om/update-state! this dissoc :modal)}
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
                                 (dom/select {:defaultValue "usd"}
                                             (dom/option {:value "usd"} "USD")
                                             (dom/option {:value "cad"} "CAD")
                                             (dom/option {:value "sek"} "SEK"))))

                             (grid/row
                               nil
                               (grid/column
                                 (grid/column-size {:small 12 :large 3})
                                 (dom/label nil "Bank country"))
                               (grid/column
                                 nil
                                 (dom/select {:defaultValue "us"}
                                             (dom/option {:value "us"} "United States")
                                             (dom/option {:value "ca"} "Canada")
                                             (dom/option {:value "se"} "Sweden")))))

                           (grid/row-column
                             nil
                             (grid/row
                               nil
                               (grid/column
                                 (grid/column-size {:small 12 :large 3})
                                 (dom/label nil "Transit number"))
                               (grid/column
                                 nil
                                 (dom/input {:placeholder "12345"
                                             :type        "text"})))
                             (grid/row
                               nil
                               (grid/column
                                 (grid/column-size {:small 12 :large 3})
                                 (dom/label nil "Institution number"))
                               (grid/column
                                 nil
                                 (dom/input {:placeholder "000"
                                             :type        "text"})))
                             (grid/row
                               nil
                               (grid/column
                                 (grid/column-size {:small 12 :large 3})
                                 (dom/label nil "Account number"))
                               (grid/column
                                 nil
                                 (dom/input {:type "text"})))))



                  (dom/div
                    (css/callout (css/text-align :right))
                    (dom/p (css/add-class :header))
                    (dom/a (->> {:onClick #(om/update-state! this dissoc :modal)}
                                (css/button-hollow)) (dom/span nil "Cancel"))
                    (dom/a
                      (->> {:onClick #(om/update-state! this dissoc :modal)}
                           (css/button)) (dom/span nil "Save"))))))))

        (dom/div
          (css/callout)
          (dom/p (css/add-class :header))
          ;(dom/div
          ;  (css/text-align :right)
          ;  (dom/a (css/button) (dom/span nil "Save")))
          )
        ))))

(def ->Payouts (om/factory Payouts))