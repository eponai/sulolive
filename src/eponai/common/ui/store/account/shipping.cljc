(ns eponai.common.ui.store.account.shipping
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]))

(defn shipping-options [component]
  (dom/div
    nil
    ;(grid/row-column
    ;  nil)

    ;(grid/row
    ;  nil
    ;  (grid/column
    ;    (grid/column-size {:small 12 :medium 3 :large 2})
    ;    (dom/label nil "Allow free pickup"))
    ;  (grid/column
    ;    nil
    ;    (dom/div
    ;      (css/add-class :switch)
    ;      (dom/input
    ;        (->> {:id   "shipping-switch"
    ;              :type "checkbox"
    ;              :name "shipping-switch"}
    ;             (css/add-class :switch-input)))
    ;      (dom/label
    ;        (->> {:htmlFor "shipping-switch"}
    ;             (css/add-class :switch-paddle))
    ;        (dom/span (css/add-class :show-for-sr) "Allow Free Pickup")
    ;        (dom/span (css/add-class :switch-inactive) "No")
    ;        (dom/span (css/add-class :switch-active) "Yes")))))
    (grid/row-column
      nil
      (grid/row
        nil
        (grid/column
          (grid/column-size {:small 12 :large 2})
          (dom/label nil "Shipping rate"))
        (grid/column
          nil
          (dom/input {:type         "number"
                      :defaultValue "0.00"})))
      (grid/row
        nil
        (grid/column
          (grid/column-size {:small 12 :large 2})
          (dom/label nil "Delivery time (days)"))
        (grid/column
          nil
          (dom/input {:type         "number"
                      :step         1
                      :defaultValue "1"}))
        (grid/column
          (css/add-class :shrink)
          (dom/span nil "-"))
        (grid/column
          nil
          (dom/input {:type         "number"
                      :defaultValue "2"})))
      (grid/row
        nil
        (grid/column
          (grid/column-size {:small 12 :large 2})
          (dom/label nil "Additional info"))
        (grid/column
          nil
          (dom/input {:type "text"}))))
    ;(dom/div
    ;  (css/grid-column)
    ;  (dom/input {:type "radio" :name "entity-type" :id "entity-type-individual" :value "individual" :defaultChecked true})
    ;  (dom/label {:htmlFor "entity-type-individual"} "Individual")
    ;  (dom/input {:type "radio" :name "entity-type" :id "entity-type-company" :value "company"})
    ;  (dom/label {:htmlFor "entity-type-company"} "Company")
    ;  )
    (grid/row-column
      (css/text-align :right)
      (dom/a
        (css/button)
        (dom/span nil "Save")))))