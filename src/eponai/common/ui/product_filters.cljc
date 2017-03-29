(ns eponai.common.ui.product-filters
  (:require
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]))


(defui ProductFilters
  Object
  (render [this]
    (dom/div
      nil
      (dom/h3 (css/add-class :header) "Filters")
      (dom/div nil (dom/span nil "Render some filters here please"))
      (dom/div
        nil
        (dom/a (css/button) (dom/span nil "Apply"))
        (dom/a (css/button-hollow) (dom/span nil "Cancel"))))))

(def ->ProductFilters (om/factory ProductFilters))