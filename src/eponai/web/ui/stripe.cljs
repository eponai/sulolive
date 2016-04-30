(ns eponai.web.ui.stripe
  (:require [cljs.core.async :refer [chan <! put!]]
            [clojure.walk :refer [keywordize-keys]]
            [eponai.client.ui :refer-macros [opts]]
            [goog.string :as gstring]
            [goog.string.format]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [info debug error trace]]
            [eponai.web.ui.utils :as utils]
            [eponai.web.ui.format :as f])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn load-checkout [channel]
  (-> (goog.net.jsloader.load "https://checkout.stripe.com/v2/checkout.js")
      (.addCallback #(put! channel [:stripe-checkout-loaded :success]))))

(defn checkout-loaded? []
  (boolean (aget js/window "StripeCheckout")))

(defn stripe-token-recieved-cb [component plan-id]
  (fn [token]
    (let [clj-token (keywordize-keys (js->clj token))]
      (debug "Recieved token from Stripe.")
      (trace "Recieved token from Stripe: " clj-token)
      (om/transact! component `[(stripe/subscribe ~{:token clj-token
                                                    :plan plan-id})
                                :query/stripe]))))

(defn open-checkout [component plan-id args]
  (let [checkout (.configure js/StripeCheckout
                             (clj->js {:key    "pk_test_KHyU4tNjwX7R0lkxDmPxvbT9"
                                       :locale "auto"
                                       :token  (stripe-token-recieved-cb component plan-id)}))]
    (.open checkout (clj->js args))))

;; ---------- UI components ----------------

(defn price->str [price]
  (gstring/format "$%.2f" price))

(defn plan-item [component props]
  (let [{:keys [plan-name
                plan-price
                plan-monthly
                user-email
                plan-id]} props]
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
                                      plan-id
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
                           :user/email]}
     {:query/stripe [:stripe/user
                     {:stripe/subscription [:stripe.subscription/id
                                            :stripe.subscription/period-end]}]}])
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
          {:keys [query/current-user
                  query/stripe]} (om/props this)]
      (debug "StripeCheckout loaded: " checkout-loaded?)
      (html
        (let [subscription (:stripe/subscription stripe)]
          (if (= :active (:stripe.subscription/status subscription))
            [:div.row.column.small-12.medium-6
             (opts {:style {:margin "1em auto"}})
             [:div.callout.clearfix
              (str "Your account is active until " (f/to-str (:stripe.subscription/period-end subscription) "yyyyMMdd"))]]
            [:div

             ;[:h2
             ; (opts {:style {:text-align "center"}})
             ; "Select your plan"]
             [:div
              (opts {:style {:text-align "center"}})
              [:h4

               "Unlimited app access"]
              [:h5.subheader " - 30-day money back guarantee."]]
             [:hr.intro-divider]

             (if checkout-loaded?
               [:div#pricePlans
                [:ul#plans
                 (plan-item this
                            {:plan-name    "Monthly"
                             :plan-price   8
                             :plan-monthly 8
                             :user-email   (:user/email current-user)
                             :plan-id      "monthly"})

                 (plan-item this
                            {:plan-name    "3 months"
                             :plan-price   21
                             :plan-monthly 7
                             :user-email   (:user/email current-user)})

                 (plan-item this
                            {:plan-name    "6 months"
                             :plan-price   36
                             :plan-monthly 6
                             :user-email   (:user/email current-user)})]]
               [:div.empty-message.text-center
                [:i.fa.fa-spinner.fa-spin.fa-3x]])
             [:div.row.column.small-12.medium-10
              [:h5 "Cancelation Policy"]
              [:p "You can cancel your subscription at any time. We will stop future charges of your card,
              and the app will be available to you for the remaining subscription period."]]]))))))
(def ->Payment (om/factory Payment))