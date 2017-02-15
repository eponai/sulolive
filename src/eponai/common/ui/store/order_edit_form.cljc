(ns eponai.common.ui.store.order-edit-form
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.common :as common]))

(defui OrderEditForm
  Object
  (render [this]
    (let [{:keys [order]} (om/props this)]
      (dom/div nil
        #?(:clj (common/loading-spinner nil))
        (my-dom/div
          (->> (css/grid-row))
          (my-dom/div
            (css/grid-column)
            (dom/h2 nil "Edit Order - " (dom/small nil (:order/id order))))
          (my-dom/div
            (->> (css/grid-column)
                 (css/text-align :right))
            (dom/a #js {:className "button hollow"
                        :onClick   #(.delete-order this)} "Delete")))
        (my-dom/div (->> (css/grid-row)
                         (css/grid-column))
                    (dom/div #js {:className "callout transparent"}
                      (dom/h4 nil (dom/span nil "Details"))))

        (my-dom/div
          (->> (css/grid-row)
               css/grid-column)
          (dom/div #js {:className "callout transparent"}
            (dom/h4 nil "Images")))

        (my-dom/div (->> (css/grid-row)
                         (css/grid-column))
                    (dom/div #js {:className "callout transparent"}
                      (dom/h4 nil (dom/span nil "Inventory"))))
        (my-dom/div (->> (css/grid-row)
                         (css/grid-column)))))))

(def ->OrderEditForm (om/factory OrderEditForm))

