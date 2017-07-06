(ns eponai.web.ui.store.finances.taxes
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.callout :as callout]))

(defui FinancesTaxes
  static om/IQuery
  (query [_]
    [:query/current-route])

  Object
  (render [this]
    (dom/div
      nil
      (callout/callout
        nil
        (menu/vertical
          (css/add-class :section-list)
          (menu/item
            nil
            (grid/row
              nil
              (grid/column
                (grid/column-size {:small 12 :medium 6})
                (dom/label nil "Taxes included"))
              (grid/column
                (grid/column-size {:small 12 :medium 6})
                (dom/label nil "Taxes included")))))))))

(def ->FinancesTaxes (om/factory FinancesTaxes))
