(ns eponai.common.ui.user.order-receipt
  (:require
    [eponai.common.ui.dom :as my-dom]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]))

(defui Order
  static om/IQuery
  (query [_]
    [:query/current-route
     :query/order])
  Object
  (render [this]
    (let [{:query/keys [current-route order]} (om/props this)
          {:keys [route route-params]} current-route]
      (debug "order: " order)
      (dom/div #js {:id "sulo-order-receipt"}
        (my-dom/div
          (->> (css/grid-row)
               css/grid-column)
          (dom/h3 nil "Order " (dom/small nil (:order-id route-params)))
          (dom/div #js {:className "callout"}
            ))))))

(def ->Order (om/factory Order))
