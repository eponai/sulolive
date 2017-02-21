(ns eponai.common.ui.user.orders
  (:require
    [eponai.common.ui.dom :as my-dom]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]))


(defui Orders
  static om/IQuery
  (query [_]
    [:query/orders])
  Object
  (render [this]
    (my-dom/div
      (->> (css/grid-row)
           css/grid-column)
      (dom/div nil "These are user orders"))))

(def ->Orders (om/factory Orders))