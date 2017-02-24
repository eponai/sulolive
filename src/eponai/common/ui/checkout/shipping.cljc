(ns eponai.common.ui.checkout.shipping
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui CheckoutShipping
  Object
  (render [this]
    (dom/div nil "Shipping yeah buddy")))

(def ->CheckoutShipping (om/factory CheckoutShipping))
