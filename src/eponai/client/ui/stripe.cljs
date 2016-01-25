(ns eponai.client.ui.stripe
  (:require [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]))

(defui Checkout
  static om/IQuery
  (query [_]
    [])
  Object
  (render [_]
    (goog.net.jsloader.load "https://checkout.stripe.com/v2/checkout.js")
    (html
      [:form.stripe-button-container
       {:action "/api/charge"
        :method "POST"}
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

(defui Payment
  Object
  (render [this]
    (html
      [:div.plans
       [:div.plan
        [:h2.plan-title
         "Monthly"]
        [:p.plan-price
         "$9"
         [:span "/mo"]]
        [:button
         {:class "btn btn-default btn-lg"}
         "Buy now"]]
       [:div
        {:class "plan plan-tall"}
        [:h2.plan-title
         "Yearly"]
        [:p.plan-price
         "$7.50"
         [:span "/mo"]]
        [:button
         {:class "btn btn-default btn-lg"}
         "Buy now"]]
       [:div.plan
        [:h2.plan-title
         "Monthly"]
        [:p.plan-price
         "$9"
         [:span "/mo"]]

        [:button
         {:class "btn btn-default btn-lg"}
         "Buy now"]]
       ])))

(def ->Payment (om/factory Payment))