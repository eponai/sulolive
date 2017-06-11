(ns eponai.web.ui.store.business
  (:require
    [eponai.client.routes :as routes]
    [eponai.client.parser.message :as msg]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.web.ui.store.common :as store-common]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.web.ui.store.business.verify :as verify]
    [eponai.common.ui.elements.input-validate :as v]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.store.finances :as finances]))

(def form-inputs verify/form-inputs)

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
           :proxy/keys [verify finances]} (om/props this)
          {:keys [store]} (om/get-computed this)
          {:keys [input-validation]} (om/get-state this)
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
            (if-let [needs-verification? (or (not accepted-tos?)
                                             (and (not-empty (get-in stripe-account [:stripe/verification :stripe.verification/fields-needed]))
                                                  (some? (:stripe.verification/due-by verification))))]
              [(dom/div
                 (css/add-class :section-title)
                 (dom/h2 nil "Verify account"))
               (callout/callout
                 nil
                 (verify/->Verify (om/computed verify
                                               {:store          store
                                                :stripe-account stripe-account})))]

              [(dom/div
                 (css/add-class :section-title)
                 (dom/h2 nil "Business"))
               (callout/callout
                 nil
                 (let [
                       ;{:keys [store]} (om/get-computed thi)
                       ;{:business/keys [input-validation]} (om/get-state component)
                       ;{:query/keys [stripe-account]} (om/props component)
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
                                         :id           (:field.legal-entity/first-name form-inputs)
                                         :placeholder  "First"}
                                        input-validation))
                             (grid/column
                               nil
                               (v/input {:type         "text"
                                         :defaultValue last-name
                                         :id           (:field.legal-entity/last-name form-inputs)
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
                                           :id           (:field.legal-entity/business-name form-inputs)
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
                         (->> {:onClick       #(.save-legal-entity this)
                               :aria-disabled true}
                              (css/add-class :disabled)
                              (css/button)) (dom/span nil "Save"))))))])))))))