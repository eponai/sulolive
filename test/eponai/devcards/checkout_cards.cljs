(ns eponai.devcards.checkout-cards
  (:require-macros
    [devcards.core :refer [defcard]])
  (:require
    [eponai.common.ui.checkout.shipping :as s]
    [eponai.common.ui.checkout.payment :as p]
    [om.dom :as dom]))

(defn checkout-container [content]
  (dom/div #js {:id "sulo-checkout"}
    content))
(defcard
  Shipping
  (checkout-container
    (s/->CheckoutShipping)))

(defcard
  Payment
  (checkout-container
    (p/->CheckoutPayment)))