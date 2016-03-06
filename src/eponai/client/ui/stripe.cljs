(ns eponai.client.ui.stripe
  (:require [cljs.core.async :refer [chan <! put!]]
            [clojure.walk :refer [keywordize-keys]]
            [eponai.client.ui :refer-macros [opts]]
            [goog.string :as gstring]
            [goog.string.format]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [info debug error trace]]
            [eponai.client.ui.utils :as utils])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn load-checkout [channel]
  (-> (goog.net.jsloader.load "https://checkout.stripe.com/v2/checkout.js")
      (.addCallback #(put! channel [:stripe-checkout-loaded :success]))))

(defn checkout-loaded? []
  (boolean (aget js/window "StripeCheckout")))

(defn stripe-token-recieved-cb [component]
  (fn [token]
    (let [clj-token (keywordize-keys (js->clj token))]
      (debug "Recieved token from Stripe.")
      (trace "Recieved token from Stripe: " clj-token)
      (om/transact! component `[(stripe/charge ~{:token clj-token})]))))

(defn open-checkout [component args]
  (let [checkout (.configure js/StripeCheckout
                             (clj->js {:key    "pk_test_KHyU4tNjwX7R0lkxDmPxvbT9"
                                       :locale "auto"
                                       :token  (stripe-token-recieved-cb component)}))]
    (.open checkout (clj->js args))))

;; ---------- UI components ----------------

(defn price->str [price]
  (gstring/format "$%.2f" price))

(defn plan-item [component props]
  (let [{:keys [plan-name
                plan-price
                plan-monthly
                user-email]} props]
    (html
      [:li.plan
       [:ul.plan-container
        [:li.title
         [:h2 plan-name]]
        [:li.price
         [:p (price->str plan-price)]]
        [:li
         [:ul.options
          [:li
           [:div
            (opts {:style {:display "inline-block"}})
            (price->str plan-monthly)]
           [:span " /month"]]]]
        [:li
         [:button
          {:on-click (fn []
                       (.show-loading component true)
                       (open-checkout component
                                      {:name        "JourMoney"
                                       :description plan-name
                                       :currency    "usd"
                                       :email       user-email
                                       :amount      (* 100 plan-price)
                                       :locale      "auto"
                                       :allowRememberMe false
                                       :opened      #(.show-loading component false)
                                       :closed      #(debug "StripeCheckout did close.")}))}
          "Buy"]]]])))

(defui Payment
  static om/IQuery
  (query [_]
    [{:query/current-user [:user/uuid
                           :user/email]}])
  Object
  (initLocalState [_]
    (let [checkout-loaded (checkout-loaded?)]
      {:checkout-loaded?   checkout-loaded
       :load-checkout-chan (chan)
       :is-loading?        (not checkout-loaded)}))
  (componentWillMount [this]
    (let [{:keys [load-checkout-chan
                  checkout-loaded?]} (om/get-state this)]
      (when-not checkout-loaded?
        (go (<! load-checkout-chan)
            (om/set-state! this {:checkout-loaded? true
                                 :is-loading? false}))
        (load-checkout load-checkout-chan))))
  (show-loading [this loading]
    (om/update-state! this assoc :is-loading? loading))
  (render [this]
    (let [{:keys [checkout-loaded?
                  is-loading?]} (om/get-state this)
          {:keys [query/current-user]} (om/props this)]
      (debug "StripeCheckout loaded: " checkout-loaded?)
      (html
        [:div.intro-message
         [:h2
          (opts {:style {:text-align "center"}})
          "Select your plan"]
         [:p
          (opts {:style {:text-align "center"}})
          "and get unlimited access to the app."]
         [:hr.intro-divider]

         (when is-loading?
           (utils/loader))

         (when checkout-loaded?
           [:div#pricePlans
            [:ul#plans
             (plan-item this
                        {:plan-name    "3 months"
                         :plan-price   25.50
                         :plan-monthly 8.50
                         :user-email (:user/email current-user)})

             (plan-item this
                        {:plan-name    "6 months"
                         :plan-price   42
                         :plan-monthly 7
                         :user-email (:user/email current-user)})

             (plan-item this
                        {:plan-name    "12 months"
                         :plan-price   60
                         :plan-monthly 5
                         :user-email (:user/email current-user)})]])]))))

(def ->Payment (om/factory Payment))