(ns eponai.common.ui.store.account
  (:require
    #?(:cljs
       [cljs.spec :as s]
       :clj
        [clojure.spec :as s])
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.utils :refer [two-decimal-price]]
    [eponai.common.ui.elements.input-validate :as v]
    ;[eponai.common.ui.store.account.activate :as activate]
    [eponai.common.ui.store.account.business :as business]
    [eponai.web.ui.store.business.verify :as verify
     ]
    ;[eponai.common.ui.store.account.general :as general]
    ;[eponai.common.ui.store.account.payouts :as payouts]
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.client.routes :as routes]
    [eponai.client.parser.message :as msg]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.web.ui.store.finances :as finances]
    [eponai.web.ui.store.common :as store-common]
    ))

(def form-inputs verify/form-inputs)

;(defn tabs-panel [is-active? & content]
;  (dom/div
;    (cond->> (css/add-class :tabs-panel {:disabled true})
;             is-active?
;             (css/add-class ::css/is-active))
;    content))

;(defn tabs-title [component k opts & content]
;  (let [{:query/keys [current-route]} (om/props component)
;        {:keys [store]} (om/get-computed component)]
;    (menu/item-tab
;      (merge opts
;             {:is-active? (= (:route current-route) k)})
;      (dom/a
;        {:href (routes/url k {:store-id (:db/id store)})}
;        content))))

(defn label-column [opts & content]
  (grid/column
    (grid/column-size {:small 12 :large 3} opts)
    content))

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
     {:proxy/verify (om/get-query verify/Verify)}
     ;{:proxy/payouts (om/get-query payouts/Payouts)}
     ;{:proxy/general (om/get-query general/General)}
     {:proxy/finances (om/get-query finances/StoreFinances)}
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
           :proxy/keys [verify payouts general finances]} (om/props this)
          {:keys [store]} (om/get-computed this)
          {:keys [active-tab]} (om/get-state this)
          {:stripe/keys [verification]} stripe-account
          accepted-tos? (not (some #(clojure.string/starts-with? % "tos_acceptance") (:stripe.verification/fields-needed verification)))
          {:keys [route route-params]} current-route
          ]

      (debug "Finances: " finances)
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
              ;(menu/item
              ;  (when (= route :store-dashboard/business#verify)
              ;    (css/add-class :is-active))
              ;  (dom/a {:href (routes/url :store-dashboard/business#verify route-params)}
              ;         (dom/span nil "Verify")))
              (menu/item
                (when (= route :store-dashboard/business)
                  (css/add-class :is-active))
                (dom/a {:href (routes/url :store-dashboard/business route-params)}
                       (dom/span nil "Business info"))))
            ))


        ;(dom/div
        ;  (css/add-class :section-title))
        (dom/h1 (css/show-for-sr) "Business")

        ;(callout/callout
        ;  nil)


        (cond

              ;(= route :store-dashboard/settings#payouts)
              ;(finances/->StoreFinances finances)

              (= route :store-dashboard/business)
              (dom/div
                nil
                (let [needs-verification? (or (not accepted-tos?)
                                              (and (not-empty (get-in stripe-account [:stripe/verification :stripe.verification/fields-needed]))
                                                   (some? (:stripe.verification/due-by verification))))]
                  (when needs-verification?
                    [(dom/div
                       (css/add-class :section-title)
                       (dom/h2 nil "Verify account"))
                     (callout/callout
                       nil
                       (verify/->Verify (om/computed verify
                                                     {:store          store
                                                      :stripe-account stripe-account})))]))

                (when (:stripe/details-submitted? stripe-account)
                  [(dom/div
                     (css/add-class :section-title)
                     (dom/h2 nil "Business"))
                   (callout/callout
                     nil
                     (defn account-details [component]
                       (let [{:keys [store]} (om/get-computed component)
                             {:business/keys [input-validation]} (om/get-state component)
                             {:query/keys [stripe-account]} (om/props component)
                             legal-entity (:stripe/legal-entity stripe-account)
                             {:stripe.legal-entity/keys [address type first-name last-name business-name]} legal-entity
                             {:stripe.legal-entity.address/keys [line1 postal city state]} address]

                         (dom/div
                           nil
                           (callout/callout-small
                             (css/add-class :warning)
                             (dom/p nil (dom/small nil "Settings are under development and this info cannot be saved. Excuse the mess, thank you for understanding.")))
                           (dom/div
                             nil
                             (callout/header nil "Business details")
                             (grid/row
                               nil
                               (label-column
                                 nil
                                 (dom/label nil "Legal Name"))
                               (grid/column
                                 nil
                                 (grid/row
                                   nil
                                   (grid/column
                                     nil
                                     (v/input {:type         "text"
                                                      :defaultValue first-name
                                                      :id (:field.legal-entity/first-name form-inputs)
                                                      :placeholder  "First"}
                                                     input-validation))
                                   (grid/column
                                     nil
                                     (v/input {:type         "text"
                                                      :defaultValue last-name
                                                      :id (:field.legal-entity/last-name form-inputs)
                                                      :placeholder  "Last"}
                                                     input-validation)))))
                             (when (= type :company)
                               (grid/row
                                 nil
                                 (label-column
                                   nil
                                   (dom/label nil "Legal Name"))
                                 (grid/column
                                   nil
                                   (grid/row
                                     nil
                                     (grid/column
                                       nil
                                       (v/input {:type         "text"
                                                        :defaultValue business-name
                                                        :id (:field.legal-entity/business-name form-inputs)
                                                        :placeholder  "Company, LTD"}
                                                       input-validation))))))

                             (grid/row
                               nil
                               (label-column
                                 nil
                                 (dom/label nil "Business Address"))
                               (grid/column
                                 nil
                                 (grid/row
                                   nil
                                   (grid/column
                                     nil
                                     (v/input
                                       {:type         "text"
                                        :id           (:field.legal-entity.address/line1 form-inputs)
                                        :defaultValue line1
                                        :placeholder  "Street"}
                                       input-validation)))
                                 (grid/row
                                   nil
                                   (grid/column
                                     nil
                                     (v/input
                                       {:type         "text"
                                        :id           (:field.legal-entity.address/postal form-inputs)
                                        :defaultValue postal
                                        :placeholder  "Postal Code"}
                                       input-validation))
                                   (grid/column
                                     nil
                                     (v/input
                                       {:type         "text"
                                        :id           (:field.legal-entity.address/city form-inputs)
                                        :defaultValue city
                                        :placeholder  "City"}
                                       input-validation))
                                   (grid/column
                                     nil
                                     (v/input
                                       {:type         "text"
                                        :id           (:field.legal-entity.address/state form-inputs)
                                        :defaultValue state
                                        :placeholder  "Province"}
                                       input-validation))))))
                           (dom/hr nil)
                           (dom/div
                             (css/text-align :right)
                             (dom/a
                               (->> {:onClick       #(.save-legal-entity component)
                                     :aria-disabled true}
                                    (css/add-class :disabled)
                                    (css/button)) (dom/span nil "Save")))))))])))))))

(def ->AccountSettings (om/factory AccountSettings))
