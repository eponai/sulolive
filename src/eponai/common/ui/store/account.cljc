(ns eponai.common.ui.store.account
  (:require
    #?(:cljs
       [cljs.spec :as s]
       :clj
        [clojure.spec :as s])
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.utils :refer [two-decimal-price]]
    [eponai.common.ui.store.account.activate :as activate]
    [eponai.common.ui.store.account.business :as business]
    [eponai.common.ui.store.account.general :as general]
    [eponai.common.ui.store.account.payouts :as payouts]
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.client.routes :as routes]
    [eponai.client.parser.message :as msg]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.web.ui.store.common :as store-common]
    ))

(defn tabs-panel [is-active? & content]
  (dom/div
    (cond->> (css/add-class :tabs-panel {:disabled true})
             is-active?
             (css/add-class ::css/is-active))
    content))

(defn tabs-title [component k opts & content]
  (let [{:query/keys [current-route]} (om/props component)
        {:keys [store]} (om/get-computed component)]
    (menu/item-tab
      (merge opts
             {:is-active? (= (:route current-route) k)})
      (dom/a
        {:href (routes/url k {:store-id (:db/id store)})}
        content))))

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
     {:proxy/activate-account (om/get-query activate/Activate)}
     {:proxy/payouts (om/get-query payouts/Payouts)}
     {:proxy/general (om/get-query general/General)}
     :query/current-route
     :query/messages])

  static store-common/IDashboardNavbarContent
  (has-subnav? [this current-route]
    (debug "Has subnav...")
    true)
  ;(render-subnav [_ current-route]
  ;  (let [{:keys [route-params route]} current-route]
  ;    (menu/horizontal
  ;      (css/align :center)
  ;      (menu/item
  ;        (when (= route :store-dashboard/settings#payouts)
  ;          (css/add-class :is-active))
  ;        (dom/a {:href (routes/url :store-dashboard/settings#payouts route-params)}
  ;               (dom/span nil "Finances")))
  ;      (menu/item
  ;        (when (= route :store-dashboard/settings#business)
  ;          (css/add-class :is-active))
  ;        (dom/a {:href (routes/url :store-dashboard/settings#business route-params)}
  ;               (dom/span nil "Business info"))))))

  Object

  (save-legal-entity [this le]
    (debug "Save Legal entity: " le)
    (let [{:keys [store]} (om/get-computed this)]
      (msg/om-transact! this `[('stripe/update-account ~{:account-params {:field/legal-entity le}
                                                         :store-id       (:db/id store)})])))

  (initLocalState [_]
    {:active-tab :payouts})
  (render [this]
    (let [{:query/keys [stripe-account current-route]
           :proxy/keys [activate-account payouts general]} (om/props this)
          {:keys [store]} (om/get-computed this)
          {:keys [active-tab]} (om/get-state this)
          accepted-tos? (not (some #(clojure.string/starts-with? % "tos_acceptance") (get-in stripe-account [:stripe/verification :stripe.verification/fields-needed])))
          {:keys [route route-params]} current-route
          ]

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
                (when (= route :store-dashboard/settings#business)
                  (css/add-class :is-active))
                (dom/a {:href (routes/url :store-dashboard/settings#business route-params)}
                       (dom/span nil "Verify account")))
              (menu/item
                (when (= route :store-dashboard/settings#payouts)
                  (css/add-class :is-active))
                (dom/a {:href (routes/url :store-dashboard/settings#payouts route-params)}
                       (dom/span nil "Finances"))))
            ))


        ;(dom/div
        ;  (css/add-class :section-title))
        (dom/h1 (css/show-for-sr) "Business")

        ;(callout/callout
        ;  nil)


        (cond

              (= route :store-dashboard/settings#payouts)

              (dom/div
                nil

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
                                (two-decimal-price 0)))
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
                  (payouts/->Payouts (om/computed payouts
                                                  {:store          store
                                                   :stripe-account stripe-account}))))

              (= route :store-dashboard/settings#business)

              (dom/div
                nil
                (let [needs-verification? (or (not (:stripe/details-submitted? stripe-account))
                                              (not-empty (get-in stripe-account [:stripe/verification :stripe.verification/fields-needed])))]
                  (when needs-verification?
                    [(dom/div
                       (css/add-class :section-title)
                       (dom/h2 nil "Verify account"))
                     (callout/callout
                       nil
                       (activate/->Activate (om/computed activate-account
                                                         {:store          store
                                                          :stripe-account stripe-account})))]))

                (when (:stripe/details-submitted? stripe-account)
                  [(dom/div
                     (css/add-class :section-title)
                     (dom/h2 nil "Business"))
                   (callout/callout
                     nil
                     (business/account-details this))])))
        ;(grid/row
        ;  (css/add-class :collapse)
        ;  ;; Tab Menu
        ;  (menu/horizontal
        ;    (css/add-class :tabs)
        ;    (cond (not (:stripe/details-submitted? stripe-account))
        ;          (tabs-title this :store-dashboard/settings#activate
        ;                      (css/add-class :activate)
        ;                      (dom/i {:classes ["fa fa-check fa-fw"]})
        ;                      (dom/small nil "Activate account"))
        ;          (not-empty (get-in stripe-account [:stripe/verification :stripe.verification/fields-needed]))
        ;          (tabs-title this :store-dashboard/settings#activate
        ;                      (css/add-class :activate)
        ;                      (dom/i {:classes ["fa fa-check fa-fw"]})
        ;                      (dom/small nil "Verify account")))
        ;
        ;    ;(tabs-title this :store-dashboard/settings
        ;    ;            nil
        ;    ;            (dom/small nil "General"))
        ;    ;(tabs-title this :store-dashboard/settings#shipping
        ;    ;            nil
        ;    ;            (dom/small nil "Shipping"))
        ;    (tabs-title this :store-dashboard/settings#payments
        ;                nil
        ;                (dom/small nil "Payments"))
        ;    (tabs-title this :store-dashboard/settings#payouts
        ;                nil
        ;                (dom/small nil "Payouts"))
        ;    (tabs-title this :store-dashboard/settings#business
        ;                nil
        ;                (dom/small nil "Business")))
        ;
        ;
        ;  ;; Content
        ;  (dom/div
        ;    (css/add-class :tabs-content)
        ;    (tabs-panel (= route :store-dashboard/settings#activate)
        ;                (activate/->Activate (om/computed activate-account
        ;                                                  {:store          store
        ;                                                   :stripe-account stripe-account})))
        ;
        ;    ;(tabs-panel (= route :store-dashboard/settings)
        ;    ;            (general/->General (om/computed general
        ;    ;                                            {:store store})))
        ;
        ;    ;(tabs-panel (= route :store-dashboard/settings#shipping)
        ;    ;            (shipping/shipping-options this))
        ;
        ;    (tabs-panel (= route :store-dashboard/settings#payments)
        ;                ;(dom/div
        ;                ;  (css/callout)
        ;                ;  (dom/p (css/add-class :header) "Payment methods")
        ;                ;  (payments/payment-methods this))
        ;                (payments/payment-methods this))
        ;
        ;    (tabs-panel (= route :store-dashboard/settings#payouts)
        ;                ;(dom/div
        ;                ;  (css/callout)
        ;                ;  (dom/p (css/add-class :header) "Payment methods")
        ;                ;  (payments/payment-methods this))
        ;                (payouts/->Payouts (om/computed payouts
        ;                                                {:store          store
        ;                                                 :stripe-account stripe-account})))
        ;
        ;
        ;    (tabs-panel (= route :store-dashboard/settings#business)
        ;                (business/account-details this)
        ;                ;(dom/div
        ;                ;  (css/callout)
        ;                ;  (dom/p (css/add-class :header) "Personal details")
        ;                ;  (business/personal-details this))
        ;                )))
        )

      ;(my-dom/div
      ;  (css/grid-row)
      ;  (my-dom/div
      ;    (->> (css/grid-column)
      ;         (css/text-align :right))
      ;    (dom/a #js {:className "button"
      ;                :onClick   #(.save-settings this)} "Save")))
      )))

(def ->AccountSettings (om/factory AccountSettings))
