(ns eponai.web.ui.store.finances
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.callout :as callout]
    [om.next :as om :refer [defui]]
    [eponai.web.ui.store.finances.settings :as settings]
    [eponai.web.ui.store.finances.taxes :as taxes]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.client.routes :as routes]
    [eponai.common :as c]
    [taoensso.timbre :refer [debug]]
    [eponai.common.format.date :as date]))

(defui StoreFinances
  static om/IQuery
  (query [_]
    [{:proxy/settings (om/get-query settings/FinancesSettings)}
     {:proxy/taxes (om/get-query taxes/FinancesTaxes)}
     {:query/stripe-balance [:stripe/balance]}
     :query/current-route])

  Object
  (render [this]
    (let [{:proxy/keys [settings taxes]
           :query/keys [current-route stripe-balance stripe-payouts]} (om/props this)
          {:keys [route route-params]} current-route
          {:keys [stripe-account]} (om/get-computed this)]

      (dom/div
        {:id "sulo-store-finances"}

        (dom/div
          (css/add-class :section-title)
          (dom/h1 nil "Finances"))
        (dom/div
          {:id "store-navbar"}
          (menu/horizontal
            nil
            (menu/item
              (when (= route :store-dashboard/finances)
                (css/add-class :is-active))
              (dom/a {:href (routes/url :store-dashboard/finances route-params)}
                     (dom/span nil "Overview")))
            (menu/item
              (when (= route :store-dashboard/finances#taxes)
                (css/add-class :is-active))
              (dom/a {:href (routes/url :store-dashboard/finances#taxes route-params)}
                     (dom/span nil "Taxes")))
            (menu/item
              (when (= route :store-dashboard/finances#settings)
                (css/add-class :is-active))
              (dom/a {:href (routes/url :store-dashboard/finances#settings route-params)}
                     (dom/span nil "Settings")))))

        (cond (= route :store-dashboard/finances)
              [
               (dom/div
                 (css/add-class :section-title)
                 (dom/h2 nil "Summary"))

               (let [balance (:stripe/balance stripe-balance)
                     available (some #(when (= (:stripe.balance/currency %) "cad") %) (:stripe.balance/available balance))
                     pending (some #(when (= (:stripe.balance/currency %) "cad") %) (:stripe.balance/pending balance))]
                 (callout/callout
                   nil
                   (grid/row
                     (css/text-align :center)
                     (grid/column
                       nil
                       (dom/h3 nil "Available for deposit")
                       (dom/div
                         (css/add-class :empty-container)
                         (dom/span (css/add-class :stat)
                                   (c/price->str (:stripe.balance/amount available)))))
                     (grid/column
                       nil
                       (dom/h3 nil "Current balance")
                       (dom/span (css/add-class :stat)
                                 (c/price->str (:stripe.balance/amount pending)))))))
               (dom/div
                 (css/add-class :section-title)
                 (dom/h2 nil "Deposits"))

               (callout/callout
                 nil
                 (dom/div
                   (css/add-class :empty-container)
                   (dom/span (css/add-class :shoutout) "No deposits made")))
               ;(callout/callout
               ;  nil
               ;  (if (empty? (:stripe/payouts stripe-payouts))
               ;    (dom/div
               ;      (css/add-class :empty-container)
               ;      (dom/span (css/add-class :shoutout) "No deposits made"))
               ;    (menu/vertical
               ;      (css/add-classes [:payout-list :section-list])
               ;      (map (fn [p]
               ;             (menu/item
               ;               nil
               ;               (dom/span nil (c/price->str (:stripe.payout/amount p)))
               ;               (dom/span nil (:stripe.payout/description p))
               ;
               ;               (dom/span nil (date/date->string (:stripe.payout/arrival-date p)))))
               ;           (:stripe/payouts stripe-payouts)))))
               ]

              (= route :store-dashboard/finances#settings)
              (settings/->FinancesSettings settings settings)

              (= route :store-dashboard/finances#taxes)
              (taxes/->FinancesTaxes taxes))))))

(def ->StoreFinances (om/factory StoreFinances))