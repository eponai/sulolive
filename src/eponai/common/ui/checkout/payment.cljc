(ns eponai.common.ui.checkout.payment
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui CheckoutPayment
  Object
  (render [this]
    (dom/div nil "Add payment and stuff")))

(def ->CheckoutPayment (om/factory CheckoutPayment))

