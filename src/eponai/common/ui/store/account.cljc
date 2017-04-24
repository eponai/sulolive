(ns eponai.common.ui.store.account
  (:require
    #?(:cljs
       [cljs.spec :as s]
       :clj
        [clojure.spec :as s])
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.format :as f]
    [eponai.common.ui.om-quill :as quill]
    [eponai.common.ui.store.account.activate :as activate]
    [eponai.common.ui.store.account.business :as business]
    [eponai.common.ui.store.account.general :as general]
    [eponai.common.ui.store.account.payments :as payments]
    [eponai.common.ui.store.account.payouts :as payouts]
    [eponai.common.ui.store.account.shipping :as shipping]
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common :as c]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.store.account.validate :as v]
    [eponai.client.routes :as routes]
    [eponai.common.ui.common :as common]))

(defn tabs-panel [is-active? & content]
  (dom/div
    (cond->> (css/add-class :tabs-panel)
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
     :query/current-route])

  Object

  (save-legal-entity [this le]
    (debug "Save Legal entity: " le))

  (initLocalState [_]
    {:active-tab    :payouts})
  (render [this]
    (let [{:query/keys [stripe-account current-route]
           :proxy/keys [activate-account payouts general]} (om/props this)
          {:keys [store]} (om/get-computed this)
          {:keys [active-tab]} (om/get-state this)
          accepted-tos? (not (some #(clojure.string/starts-with? % "tos_acceptance") (get-in stripe-account [:stripe/verification :stripe.verification/fields-needed])))
          route (:route current-route)]

      (grid/row-column
        {:id "sulo-account-settings"}
        (common/wip-label this)
        (dom/h3 nil "Settings")
        (grid/row
          (css/add-class :collapse)

          ;; Vertical Menu
          (grid/column
            (grid/column-size {:small 12 :medium 3 :large 2})
            (menu/vertical
              (css/add-class :tabs)
              (cond (not (:stripe/details-submitted? stripe-account))
                    (tabs-title this :store-dashboard/settings#activate
                                (css/add-class :activate)
                                (dom/i {:classes ["fa fa-check fa-fw"]})
                                (dom/small nil "Activate account"))
                    (not-empty (get-in stripe-account [:stripe/verification :stripe.verification/fields-needed]))
                    (tabs-title this :store-dashboard/settings#activate
                                (css/add-class :activate)
                                (dom/i {:classes ["fa fa-check fa-fw"]})
                                (dom/small nil "Verify account")))

              (tabs-title this :store-dashboard/settings
                          nil
                          (dom/small nil "General"))
              (tabs-title this :store-dashboard/settings#shipping
                          nil
                          (dom/small nil "Shipping"))
              (tabs-title this :store-dashboard/settings#payments
                          nil
                          (dom/small nil "Payments"))
              (tabs-title this :store-dashboard/settings#payouts
                          nil
                          (dom/small nil "Payouts"))
              (tabs-title this :store-dashboard/settings#business
                          nil
                          (dom/small nil "Business"))))


          ;; Content
          (grid/column
            nil
            (dom/div
              (->> (css/add-class :tabs-content)
                   (css/add-class ::css/vertical))
              (tabs-panel (= route :store-dashboard/settings#activate)
                          (activate/->Activate (om/computed activate-account
                                                            {:store store
                                                             :stripe-account stripe-account})))

              (tabs-panel (= route :store-dashboard/settings)
                          (general/->General (om/computed general
                                                          {:store store})))

              (tabs-panel (= route :store-dashboard/settings#shipping)
                          (shipping/shipping-options this))

              (tabs-panel (= route :store-dashboard/settings#payments)
                          ;(dom/div
                          ;  (css/callout)
                          ;  (dom/p (css/add-class :header) "Payment methods")
                          ;  (payments/payment-methods this))
                          (payments/payment-methods this))

              (tabs-panel (= route :store-dashboard/settings#payouts)
                          ;(dom/div
                          ;  (css/callout)
                          ;  (dom/p (css/add-class :header) "Payment methods")
                          ;  (payments/payment-methods this))
                          (payouts/->Payouts (om/computed payouts
                                                          {:store store
                                                           :stripe-account stripe-account})))


              (tabs-panel (= route :store-dashboard/settings#business)
                          (business/account-details this)
                          ;(dom/div
                          ;  (css/callout)
                          ;  (dom/p (css/add-class :header) "Personal details")
                          ;  (business/personal-details this))
                          )))))

      ;(my-dom/div
      ;  (css/grid-row)
      ;  (my-dom/div
      ;    (->> (css/grid-column)
      ;         (css/text-align :right))
      ;    (dom/a #js {:className "button"
      ;                :onClick   #(.save-settings this)} "Save")))
      )))

(def ->AccountSettings (om/factory AccountSettings))
