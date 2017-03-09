(ns eponai.devcards.checkout.shipping-cards
  (:require-macros
    [devcards.core :refer [defcard]])
  (:require
    [eponai.common.ui.checkout.shipping :as s]))

(defcard
  Shipping

  (s/->CheckoutShipping))
