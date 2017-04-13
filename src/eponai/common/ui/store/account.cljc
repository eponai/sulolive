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
    [eponai.common.ui.store.account.shipping :as shipping]
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common :as c]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.store.account.validate :as v]))

;(defn legal-entity []
;  #?(:cljs
;     (let [{:stripe.legal-entity/keys     [first-name last-name]
;            :stripe.legal-entity.dob/keys [year month day]} form-elements
;           y (c/parse-long-safe (utils/input-value-by-id year))
;           m (c/parse-long-safe (utils/input-value-by-id month))
;           d (c/parse-long-safe (utils/input-value-by-id day))
;           fn (utils/input-value-by-id first-name)
;           ln (utils/input-value-by-id last-name)
;
;           dob (when (or y m d)
;                 {:stripe.legal-entity.dob/year  y
;                  :stripe.legal-entity.dob/month m
;                  :stripe.legal-entity.dob/day   d})]
;       (not-empty
;         (f/remove-nil-keys
;           {:stripe.legal-entity/first_name fn
;            :stripe.legal-entity/last_name  ln
;            :stripe.legal-entity/dob        dob})))))



(defn tabs-panel [is-active? & content]
  (dom/div
    (cond->> (css/add-class :tabs-panel)
             is-active?
             (css/add-class ::css/is-active))
    content))

(defn tabs-title [component k opts & content]
  (let [{:keys [active-tab]} (om/get-state component)]
    (menu/item-tab
      (merge opts
             {:is-active? (= active-tab k)})
      (dom/a
        {:onClick #(om/update-state! component assoc :active-tab k)}
        content))))

(defui AccountSettings
  static om/IQuery
  (query [_]
    [{:query/stripe-account [:stripe/id
                             :stripe/country
                             :stripe/verification
                             :stripe/legal-entity
                             :stripe/external-accounts
                             :stripe/default-currency]}
     {:proxy/activate-account (om/get-query activate/Activate)}])

  Object

  (save-legal-entity [this le]
    (debug "Save Legal entity: " le))

  (initLocalState [_]
    {:active-tab    :payments})
  (render [this]
    (let [{:query/keys [stripe-account]
           :proxy/keys [activate-account]} (om/props this)
          {:keys [store]} (om/get-computed this)
          {:keys [active-tab]} (om/get-state this)
          accepted-tos? (not (some #(clojure.string/starts-with? % "tos_acceptance") (get-in stripe-account [:stripe/verification :stripe.verification/fields-needed])))]
      (grid/row-column
        {:id "sulo-account-settings"}
        (dom/h3 nil "Settings")
        (grid/row
          (css/add-class :collapse)

          ;; Vertical Menu
          (grid/column
            (grid/column-size {:small 12 :medium 3 :large 2})
            (menu/vertical
              (css/add-class :tabs)
              (when-not accepted-tos?
                (tabs-title this :activate
                            (css/add-class :activate)
                            (dom/i {:classes ["fa fa-check fa-fw"]})
                            (dom/small nil "Activate account")))

              (tabs-title this :profile
                          nil
                          (dom/small nil "General"))
              (tabs-title this :shipping
                          nil
                          (dom/small nil "Shipping"))
              (tabs-title this :payments
                          nil
                          (dom/small nil "Payments"))
              (tabs-title this :payouts
                          nil
                          (dom/small nil "Payouts"))
              (tabs-title this :business
                          nil
                          (dom/small nil "Business"))))


          ;; Content
          (grid/column
            nil
            (dom/div
              (->> (css/add-class :tabs-content)
                   (css/add-class ::css/vertical))
              (tabs-panel (= active-tab :activate)
                          (activate/->Activate (om/computed activate-account
                                                            {:store store
                                                             :stripe-account stripe-account})))

              (tabs-panel (= active-tab :profile)
                          (general/public-profile this))

              (tabs-panel (= active-tab :shipping)
                          (shipping/shipping-options this))

              (tabs-panel (= active-tab :payments)
                          ;(dom/div
                          ;  (css/callout)
                          ;  (dom/p (css/add-class :header) "Payment methods")
                          ;  (payments/payment-methods this))
                          (payments/payouts this))

              (tabs-panel (= active-tab :payouts)
                          ;(dom/div
                          ;  (css/callout)
                          ;  (dom/p (css/add-class :header) "Payment methods")
                          ;  (payments/payment-methods this))
                          (payments/payouts this))


              (tabs-panel (= active-tab :business)
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
