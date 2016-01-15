(ns eponai.client.stripe
  (:require [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]))

;<form action="/charge" method="POST">
;<script
;src="https://checkout.stripe.com/checkout.js" class="stripe-button"
;data-key="pk_test_KHyU4tNjwX7R0lkxDmPxvbT9"
;data-image="/img/documentation/checkout/marketplace.png"
;data-name="Demo Site"
;data-description="2 widgets"
;data-currency="sek"
;data-amount="2000"
;data-locale="auto">
;</script>
;</form>

(defui Checkout
  static om/IQuery
  (query [_]
    [])
  Object
  (render [_]
    (println "Rendering")
    (goog.net.jsloader.load "https://checkout.stripe.com/v2/checkout.js")
    (html
      [:form
       {:action "/api/charge"
        :method "POST"
        :class "stripe-button-container"}
       [:script
        {:src              "https://checkout.stripe.com/checkout.js"
         :class            "stripe-button"
         :data-key         "pk_test_KHyU4tNjwX7R0lkxDmPxvbT9"
         :data-name        "Test Checkout"
         :data-description "Testing this out"
         :data-currency    "sek"
         :data-amount      "200"
         :data-locale      "auto"}]])))

(def ->Checkout (om/factory Checkout))