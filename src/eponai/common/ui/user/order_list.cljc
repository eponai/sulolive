(ns eponai.common.ui.user.order-list
  (:require
    [eponai.common.ui.dom :as my-dom]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]))


(defui OrderList
  static om/IQuery
  (query [_]
    [:query/orders])
  Object
  (render [this]
    (my-dom/div
      (->> (css/grid-row)
           css/grid-column)
      (dom/div nil "These are user orders"))))

(def ->OrderList (om/factory OrderList))