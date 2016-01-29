(ns eponai.client.ui.stripe
  (:require [cljs.core.async :refer [chan <! put!]]
            [eponai.client.ui :refer-macros [opts]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;(defui Checkout
;  static om/IQuery
;  (query [_]
;    [])
;  Object
;  (render [_]
;    (html
;      [:form.stripe-button-container
;       {:action "/api/charge"
;        :method "POST"}
      ;[:script

;       <script>
;       var handler = StripeCheckout.configure({
;                                               key: 'pk_test_KHyU4tNjwX7R0lkxDmPxvbT9',
;                                               image: '/img/documentation/checkout/marketplace.png',
;locale: 'auto',
;token: function(token) {
;                        // Use the token to create the charge with a server-side script.
;                         // You can access the token ID with `token.id`
;                             }
;                        });
;
;$('#customButton').on('click', function(e) {
;                                            // Open Checkout with further options
;                                             handler.open({
;                                                           name: 'JourMoney',
;                                                           description: '2 widgets',
;                                                           currency: "sek",
;                                                           amount: 2000
;                                                           });
;                                             e.preventDefault();
;                                            });
;
;   // Close Checkout on page navigation
;   $(window).on('popstate', function() {
;                                        handler.close();
;                                        });
;   </script>
;       {:src              "https://checkout.stripe.com/checkout.js"
;        :class            "stripe-button"
;        :data-key         "pk_test_KHyU4tNjwX7R0lkxDmPxvbT9"
;        :data-name        "Test Checkout"
;        :data-description "Testing this out"
;        :data-currency    "sek"
;        :data-amount      "200"
;        :data-locale      "auto"}]
;       ])))
;
;(def ->Checkout (om/factory Checkout))

(defn load-checkout [channel]
  (-> (goog.net.jsloader.load "https://checkout.stripe.com/v2/checkout.js")
      (.addCallback #(put! channel [:stripe-checkout-loaded :success]))))

(defn checkout-loaded? []
  (aget js/window "StripeCheckout"))

(defn open-checkout [args]
  (let [checkout (.configure js/StripeCheckout
                             (clj->js {:key    "pk_test_KHyU4tNjwX7R0lkxDmPxvbT9"
                                       :locale "auto"
                                       :token  #(prn "Token recieved: " %)}))]
    (.open checkout (clj->js args))))


(defui Payment
  Object
  (initLocalState [this]
    {:checkout-loaded? (checkout-loaded?)
     :load-checkout-chan (chan)})
  (componentWillMount [this]
    (let [{:keys [load-checkout-chan
                  checkout-loaded?]} (om/get-state this)]
      (when-not checkout-loaded?
        (go (<! load-checkout-chan)
            (om/set-state! this {:checkout-loaded? true}))
        (load-checkout load-checkout-chan))))
  (render [this]
    (let [{:keys [checkout-loaded?]} (om/get-state this)]
      (println "Rendering loaded: " checkout-loaded?)
      (html
        (if checkout-loaded?
          [:div
           [:h3
            (opts {:style {:text-align "center"}})
            "Select your plan"]
           [:hr.intro-divider]

           [:div#pricePlans

            [:ul#plans
             [:li.plan
              [:ul.plan-container
               [:li.title
                [:h2 "Monthly"]]
               [:li.price
                [:p "$9.90"]]
               [:li
                [:ul.options
                 [:li
                  [:div
                   (opts {:style {:display "inline-block"}})
                   "$9.90"]
                  [:span " /month"]]]]
               [:li.button
                [:a
                 {:on-click #(open-checkout {:name        "JourMoney"
                                             :description "Somethign"
                                             :currency    "usd"
                                             :amount      990})}
                 "Buy"]]]]

             [:li.plan
              [:ul.plan-container
               [:li.title
                [:h2 "Yearly"]]
               [:li.price
                [:p "$90.00"]]
               [:li
                [:ul.options
                 [:li
                  [:div
                   (opts {:style {:display "inline-block"}})
                   "$7.50"]
                  [:span " /month"]]]]
               [:li.button
                [:a
                 {:on-click #(open-checkout {:name        "JourMoney"
                                             :description "Somethign"
                                             :currency    "usd"
                                             :amount      9000})}
                 "Buy"]]]]]
            ]]
          [:div.loader
           "Loading..."])))))

(def ->Payment (om/factory Payment))