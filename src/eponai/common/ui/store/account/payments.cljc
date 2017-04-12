(ns eponai.common.ui.store.account.payments
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.icons :as icons]
    [om.next :as om]))

(defn payment-methods [component]
  (dom/div
    nil
    (dom/div
      (css/add-class :payment-methods)
      (icons/visa-card)
      (icons/mastercard)
      (icons/american-express))
    (dom/span nil "SULO Live currently supports the above payment methods. We'll keep working towards adding support for more options.")))

(defn payouts [component]
  (let [{:keys [modal]} (om/get-state component)
        {:query/keys [stripe-account]} (om/props component)
        {:stripe/keys [external-accounts]} stripe-account]
    (dom/div
      nil
      (grid/row
        nil
        (grid/column
          (grid/column-size {:small 12 :large 2})
          (dom/label nil "Bank Account"))
        (grid/column
          nil
          (if (not-empty external-accounts)
            (map-indexed
              (fn [i bank-acc]
                (let [{:stripe.external-account/keys [bank-name currency last4 country]} bank-acc]
                  (grid/row
                    nil
                    (grid/column
                      {:classes [:bank-account]}
                      (dom/div {:classes ["bank-detail bank-name"]}
                               (dom/span nil bank-name))
                      (dom/div {:classes ["bank-detail"]}
                               (dom/span nil "/"))
                      (dom/div {:classes ["bank-detail account"]}
                               (map (fn [i]
                                      (dom/span nil "•"))
                                    (take 4 (range)))
                               (dom/span nil last4))
                      (dom/div {:classes ["bank-detail currency"]}
                               (dom/span nil currency))
                      (dom/div {:classes ["bank-detail country"]}
                               (dom/span nil (str "(" country ")"))))
                    (grid/column
                      nil
                      (dom/a
                        (->> {:onClick #(om/update-state! component assoc :modal :bank-account)}
                             (css/button-hollow)) (dom/span nil "Update"))
                      ))))
              external-accounts)
            (dom/a
              (css/button-hollow)
              (dom/span nil "Add bank account..."))))
        (when (= modal :bank-account)
          (common/modal
            {:on-close #(om/update-state! component dissoc :modal)}
            (dom/div
              nil
              (dom/h4 (css/add-class :header) "Your bank account")
              (dom/p (css/text-align :center) (dom/small nil "Your bank account must be a checking account."))
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
                    (dom/input {:type "text"}))))



              (dom/div
                (css/text-align :right)
                (dom/a (->> {:onClick #(om/update-state! component dissoc :modal)}
                            (css/button-hollow)) (dom/span nil "Cancel"))
                (dom/a
                  (->> {:onClick #(om/update-state! component dissoc :modal)}
                       (css/button)) (dom/span nil "Save")))))))
      (grid/row
        (css/align :middle)
        (grid/column
          (grid/column-size {:small 12 :large 2})
          (dom/label nil "Payout schedule"))

        (grid/column
          (css/add-class :payout-schedule)
          (dom/strong nil "Daily ")
          (dom/span nil " — 7 day rolling basis"))
        (grid/column
          (css/align :right)
          (dom/a
            (->> {:onClick #(om/update-state! component assoc :modal :payout-schedule)}
                 (css/button-hollow))
            (dom/span nil "Change schedule")))
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
                (css/text-align :right)
                (dom/a (->> {:onClick #(om/update-state! component dissoc :modal)}
                            (css/button-hollow)) (dom/span nil "Cancel"))
                (dom/a
                  (->> {:onClick #(om/update-state! component dissoc :modal)}
                       (css/button)) (dom/span nil "Save"))))))))))