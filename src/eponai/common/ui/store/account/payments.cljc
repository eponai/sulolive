(ns eponai.common.ui.store.account.payments
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.icons :as icons]
    [om.next :as om]
    [taoensso.timbre :refer [debug]]))



(defn payment-methods [component]
  (dom/div
    nil
    (dom/div
      (css/add-class :payment-methods)
      (icons/visa-card)
      (icons/mastercard)
      (icons/american-express))
    (dom/span nil "SULO Live currently suppor ts the above payment methods. We'll keep working towards adding support for more options.")))

