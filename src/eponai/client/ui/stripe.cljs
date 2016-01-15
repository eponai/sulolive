(ns eponai.client.ui.stripe
  (:require [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]))

;<form action="" method="POST" id="payment-form">
;<span class="payment-errors"></span>
;
;<div class="form-row">
;<label>
;<span>Card Number</span>
;<input type="text" size="20" data-stripe="number"/>
;</label>
;</div>
;
;<div class="form-row">
;<label>
;<span>CVC</span>
;<input type="text" size="4" data-stripe="cvc"/>
;</label>
;</div>
;
;<div class="form-row">
;<label>
;<span>Expiration (MM/YYYY)</span>
;<input type="text" size="2" data-stripe="exp-month"/>
;</label>
;<span> / </span>
;<input type="text" size="4" data-stripe="exp-year"/>
;</div>
;
;<button type="submit">Submit Payment</button>
;</form>

(defn on-make-payment-click []
  (fn []
    (println "Submitting payment...")))

(defui Checkout
  static om/IQuery
  (query [_]
    [])
  Object
  (render [_]
    (println "Rendering")
    (goog.net.jsloader.load "https://checkout.stripe.com/v2/checkout.js")
    (html
      [:form.stripe-button-container
       {:action "/api/charge"
        :method "POST"}
      ; [:span.payment-errors]
      ; [:div.form-row
      ;  [:label
      ;   [:span "Card Number"]
      ;   [:input.form-control
      ;    {:type        "text"
      ;     :size        "20"
      ;     :data-stripe "number"}]]]
      ;
      ; [:div.form-row
      ;  [:label
      ;   [:span "CVC"]
      ;   [:input.form-control
      ;    {:type        "text"
      ;     :size        "4"
      ;     :data-stripe "cvc"}]]]
      ; [:div.form-row
      ;  [:label
      ;   [:span "Expiration (MM/YYYY)"]
      ;   [:input.form-control
      ;    {:type        "text"
      ;     :size        "2"
      ;     :data-stripe "exp-month"}]
      ;   [:span "/"]
      ;   [:input.form-control
      ;    {:type        "text"
      ;     :size        "4"
      ;     :data-stripe "exp-year"}]]]
      ; [:button {:class "btn btn-primary btn-lg"
      ;           :on-click (on-make-payment-click)}
      ;  "Submit Payment"]]
      [:script
       {:src              "https://checkout.stripe.com/checkout.js"
        :class            "stripe-button"
        :data-key         "pk_test_KHyU4tNjwX7R0lkxDmPxvbT9"
        :data-name        "Test Checkout"
        :data-description "Testing this out"
        :data-currency    "sek"
        :data-amount      "200"
        :data-locale      "auto"}]]
      )))

(def ->Checkout (om/factory Checkout))