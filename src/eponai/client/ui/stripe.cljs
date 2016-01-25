(ns eponai.client.ui.stripe
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer-macros [opts]]
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
      [:div#pricePlans
       [:ul#plans
        [:li.plan
         [:ul.plan-container
          [:li.title
           [:h2 "Montly"]]
          [:li.price
           [:p "$9.90/"
            [:span "mo"]]]
          [:li.button
           [:a "Buy"]]]]

        [:li.plan
         [:ul.plan-container
          [:li.title
           [:h2 "Yearly"]]
          [:li.price
           [:p "$7.50/"
            [:span "mo"]]]
          [:li.button
           [:a "Buy"]]]]]
       ])))

(def ->Payment (om/factory Payment))