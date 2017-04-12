(ns eponai.common.ui.store.account.business
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.input-validate :as validate]
    [om.next :as om]))

(defn account-details [component]
  (let [{:keys [form-elements]} (om/get-state component)]
    (dom/div
      nil
      (grid/row
        nil
        (grid/column
          (grid/column-size {:small 12 :medium 3 :large 2})
          (dom/label nil "Business Type"))
        (grid/column
          nil
          (dom/input {:type "radio" :name "entity-type" :id "entity-type-individual" :value "individual" :defaultChecked true})
          (dom/label {:htmlFor "entity-type-individual"} "Individual")
          (dom/input {:type "radio" :name "entity-type" :id "entity-type-company" :value "company"})
          (dom/label {:htmlFor "entity-type-company"} "Company")
          ))

      (grid/row
        nil
        (grid/column
          (grid/column-size {:small 12 :medium 3 :large 2})
          (dom/label nil "Legal Name"))
        (grid/column
          nil
          (dom/input {:type        "text"
                      :placeholder "Company, LTD"})))

      (grid/row
        nil
        (grid/column
          (grid/column-size {:small 12 :medium 3 :large 2})
          (dom/label nil "Business Address"))
        (grid/column
          nil
          (grid/row
            nil
            (grid/column
              nil
              (dom/input {:type        "text"
                          :id          (:stripe.address/street form-elements)
                          :placeholder "Street"})))
          (grid/row
            nil
            (grid/column
              nil
              (dom/input {:type        "text"
                          :id          (:stripe.address/postal form-elements)
                          :placeholder "Postal Code"}))
            (grid/column
              nil
              (dom/input {:type        "text"
                          :id          (:stripe.address/locality form-elements)
                          :placeholder "City"}))
            (grid/column
              nil
              (dom/input {:type        "text"
                          :id          (:stripe.addess/state form-elements)
                          :placeholder "Province"}))))))))

(defn personal-details [component]
  (let [{:query/keys [stripe-account]} (om/props component)
        {:keys [validation-error form-elements]} (om/get-state component)]
    (grid/row-column
      (css/align :center)
      (grid/row
        nil
        (grid/column
          (grid/column-size {:small 12 :medium 3 :large 2})
          (dom/label nil "Legal Name"))
        (grid/column
          nil
          (grid/row
            nil
            (grid/column
              nil
              (dom/input {:type         "text"
                          :id           (:stripe.legal-entity/first-name form-elements)
                          :placeholder  "First"
                          :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/first-name])}))
            (grid/column
              nil
              (dom/input {:type         "text"
                          :id           (:stripe.legal-entity/last-name form-elements)
                          :placeholder  "Last"
                          :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/last-name])})))))
      (grid/row
        nil
        (grid/column
          (grid/column-size {:small 12 :medium 3 :large 2})
          (dom/label nil "Date of birth"))
        (grid/column
          nil
          (grid/row
            nil
            (grid/column
              nil
              (validate/input
                :stripe.legal-entity.dob/day
                {:id           (:stripe.legal-entity.dob/day form-elements)
                 :type         "number"
                 :placeholder  "Day"
                 :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/dob :stripe.legal-entity.dob/day])}
                validation-error)
              ;(dom/input #js {:type         "text"
              ;                :id           (:stripe.legal-entity.dob/day form-elements)
              ;                :placeholder  "Day"
              ;                :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/dob :stripe.legal-entity.dob/day])})
              )
            (grid/column
              nil
              (validate/input
                :stripe.legal-entity.dob/month
                {:type         "number"
                 :id           (:stripe.legal-entity.dob/month form-elements)
                 :placeholder  "Month"
                 :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/dob :stripe.legal-entity.dob/month])}
                validation-error))

            (grid/column
              nil
              (validate/input
                :stripe.legal-entity.dob/year
                {:type         "number"
                 :id           (:stripe.legal-entity.dob/year form-elements)
                 :placeholder  "Year"
                 :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/dob :stripe.legal-entity.dob/year])}
                validation-error))))))))