(ns eponai.common.ui.store.account.business
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.input-validate :as validate]
    [eponai.common.ui.store.account.validate :as v]
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.next :as om]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.callout :as callout]))

(defn label-column [opts content]
  (grid/column
    (->> opts
         (grid/column-size {:small 12 :medium 3 :large 2}))
    content))

(def prefix-key "business-details-")

(defn prefixed-id [k]
  (str prefix-key (get v/form-inputs k)))

(defn save-legal-entity [component]
  #?(:cljs
     (let [street (utils/input-value-by-id (prefixed-id :field.legal-entity.address/line1))
           postal (utils/input-value-by-id (prefixed-id :field.legal-entity.address/postal))
           city (utils/input-value-by-id (prefixed-id :field.legal-entity.address/city))
           state (utils/input-value-by-id (prefixed-id :field.legal-entity.address/state))

           first-name (utils/input-value-by-id (prefixed-id :field.legal-entity/first-name))
           last-name (utils/input-value-by-id (prefixed-id :field.legal-entity/last-name))

           legal-entity {:field.legal-entity/address    {:field.legal-entity.address/line1  street
                                                         :field.legal-entity.address/postal postal
                                                         :field.legal-entity.address/city   city
                                                         :field.legal-entity.address/state  state}
                         :field.legal-entity/first-name first-name
                         :field.legal-entity/last-name  last-name}
           validation (v/validate :field/legal-entity legal-entity prefix-key)]
       (when (nil? validation)
         (.save-legal-entity component legal-entity))
       (om/update-state! component assoc :business/input-validation validation))))

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
                (validate/input {:type         "text"
                                 :defaultValue first-name
                                 :id (prefixed-id :field.legal-entity/first-name)
                                 :placeholder  "First"}
                                input-validation))
              (grid/column
                nil
                (validate/input {:type         "text"
                                 :defaultValue last-name
                                 :id (prefixed-id :field.legal-entity/last-name)
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
                  (validate/input {:type         "text"
                                   :defaultValue business-name
                                   :id (prefixed-id :field.legal-entity/business-name)
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
                (validate/input
                  {:type         "text"
                   :id           (prefixed-id :field.legal-entity.address/line1)
                   :defaultValue line1
                   :placeholder  "Street"}
                  input-validation)))
            (grid/row
              nil
              (grid/column
                nil
                (validate/input
                  {:type         "text"
                   :id           (prefixed-id :field.legal-entity.address/postal)
                   :defaultValue postal
                   :placeholder  "Postal Code"}
                  input-validation))
              (grid/column
                nil
                (validate/input
                  {:type         "text"
                   :id           (prefixed-id :field.legal-entity.address/city)
                   :defaultValue city
                   :placeholder  "City"}
                  input-validation))
              (grid/column
                nil
                (validate/input
                  {:type         "text"
                   :id           (prefixed-id :field.legal-entity.address/state)
                   :defaultValue state
                   :placeholder  "Province"}
                  input-validation))))))
      (dom/hr nil)
      (dom/div
        (css/text-align :right)
        (dom/a
          (->> {:onClick       #(save-legal-entity component)
                :aria-disabled true}
               (css/add-class :disabled)
               (css/button)) (dom/span nil "Save"))))))