(ns eponai.devcards.ui.stripe-dc
  (:require [devcards.core :as dc]
            [sablono.core :refer-macros [html]]
            [eponai.client.ui.stripe :as stripe])
  (:require-macros [devcards.core :refer [defcard]]))

(defcard
  stripe-checkout
   (stripe/->Checkout {:data-amount "1000"}))