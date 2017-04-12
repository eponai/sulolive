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
        [eponai.common.ui.elements.grid :as grid]))

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

(defn tabs-title [component k & content]
  (let [{:keys [active-tab]} (om/get-state component)]
    (menu/item-tab
      {:is-active? (= active-tab k)}
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
                             :stripe/external-accounts]}
     {:proxy/activate-account (om/get-query activate/Activate)}])

  Object
  ;#?(:cljs
  ;   (save-settings
  ;     [this]
  ;     (let [{:keys [store]} (om/get-computed this)
  ;           {:keys [quill-editor]} (om/get-state this)
  ;           le (legal-entity)
  ;           validation-error (s/explain-data :stripe/legal-entity le)]
  ;
  ;
  ;       (when (nil? validation-error)
  ;         (om/transact! this `[(store/update-info ~(-> store
  ;                                                      (assoc :store/description (quill/get-HTML quill-editor))
  ;                                                      (assoc :store/name (utils/input-value-by-id (:store.info/name form-elements)))))
  ;                              (stripe/update-account ~{:params   {:legal_entity le}
  ;                                                       :store-id (:db/id store)})
  ;                              :query/store
  ;                              :query/stripe-account]))
  ;       (om/update-state! this assoc :validation-error validation-error))))
  ;#?(:cljs
  ;   (validate-input
  ;     [this]
  ;     (om/update-state! this assoc :validation-error (s/explain-data :stripe/legal-entity (legal-entity)))))
  (initLocalState [_]
    {:active-tab    :activate})
  (render [this]
    (let [{:query/keys [stripe-account]
           :proxy/keys [activate-account]} (om/props this)
          {:keys [store]} (om/get-computed this)
          {:keys [active-tab]} (om/get-state this)]
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
              (tabs-title this :activate
                          (dom/i {:classes ["fa fa-check fa-fw"]})
                          (dom/small nil "Activate account"))

              (tabs-title this :profile
                          (dom/small nil "General"))
              (tabs-title this :shipping
                          (dom/small nil "Shipping"))
              (tabs-title this :payments
                          (dom/small nil "Payments"))
              (tabs-title this :business-settings
                          (dom/small nil "Business Settings"))))


          ;; Content
          (grid/column
            nil
            (dom/div
              (->> (css/add-class :tabs-content)
                   (css/add-class ::css/vertical))
              (tabs-panel (= active-tab :activate)
                          (activate/->Activate (om/computed activate-account
                                                            {:store store})))

              (tabs-panel (= active-tab :profile)
                          (general/public-profile this))

              (tabs-panel (= active-tab :shipping)
                          (dom/div
                            (css/callout)
                            (dom/p (css/add-class :header) "Shipping")
                            (shipping/shipping-options this)))

              (tabs-panel (= active-tab :payments)
                          (dom/div
                            (css/callout)
                            (dom/p (css/add-class :header) "Payment methods")
                            (payments/payment-methods this))
                          (dom/div
                            (css/callout)
                            (dom/p (css/add-class :header) "Payouts")
                            (payments/payouts this)))


              (tabs-panel (= active-tab :business-settings)
                          ;(dom/div
                          ;  (css/callout)
                          ;  (dom/p (css/add-class :header) "Business details")
                          ;  (business/account-details this))
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
