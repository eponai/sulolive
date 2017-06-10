(ns eponai.web.ui.store.finances
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.utils :as ui-utils]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.callout :as callout]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.store.account.payouts :as payouts]
    [eponai.common.ui.common :as common]
    [eponai.web.ui.store.common :as store-common]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.client.routes :as routes]
    [taoensso.timbre :refer [debug]]))

(defui StoreFinances
  static om/IQuery
  (query [_]
    [{:proxy/payouts (om/get-query payouts/Payouts)}
     ;{:proxy/navbar (om/get-query nav/Navbar)}
     ;{:query/store [:store/profile]}
     :query/current-route])
  static store-common/IDashboardNavbarContent
  (has-subnav? [_ _] true)

  Object
  (render [this]
    (let [{:proxy/keys [payouts]
           :query/keys [current-route]} (om/props this)
          {:keys [route route-params]} current-route
          {:keys [stripe-account]} (om/get-computed this)]
      ;(common/page-container
      ;  {:navbar navbar :id "sulo-store-finances"})
      (debug "Current route: " route)
      (dom/div
        {:id "sulo-store-finances"}

        (dom/div
          (->> {:id "store-navbar"}
               (css/add-class :navbar-container))
          (dom/nav
            (->> (css/add-class :navbar)
                 (css/add-class :top-bar))
            (menu/horizontal
              (css/align :center)
              (menu/item
                (when (= route :store-dashboard/finances)
                  (css/add-class :is-active))
                (dom/a {:href (routes/url :store-dashboard/finances route-params)}
                       (dom/span nil "Overview")))
              (menu/item
                (when (= route :store-dashboard/finances#settings)
                  (css/add-class :is-active))
                (dom/a {:href (routes/url :store-dashboard/finances#settings route-params)}
                       (dom/span nil "Settings"))))))

        (cond (= route :store-dashboard/finances)
              [
               (dom/div
                 (css/add-class :section-title)
                 (dom/h2 nil "Summary"))

               (callout/callout
                 nil
                 (grid/row
                   (css/text-align :center)
                   (grid/column
                     nil
                     (dom/h3 nil "Current balance")
                     (dom/span (css/add-class :stat)
                               (ui-utils/two-decimal-price 0)))
                   (grid/column
                     nil
                     (dom/h3 nil "Next deposit")
                     (dom/div
                       (css/add-class :empty-container)
                       (dom/span (css/add-class :shoutout) "No planned")))))
               (dom/div
                 (css/add-class :section-title)
                 (dom/h2 nil "Deposits"))
               (callout/callout
                 nil
                 (dom/div
                   (css/add-class :empty-container)
                   (dom/span (css/add-class :shoutout) "No deposits made")))]

              (= route :store-dashboard/finances#settings)

              [
               (dom/div
                 (css/show-for-sr (css/add-class :section-title))
                 (dom/h1 nil "Deposit settings"))
               (dom/div
                 (css/add-class :section-title)
                 (dom/h2 nil "Deposit schedule"))
               (payouts/->Payouts payouts (om/computed payouts
                                                       {:stripe-account stripe-account}))])))))

(def ->StoreFinances (om/factory StoreFinances))