(ns eponai.common.ui.checkout.stripe
  (:require-macros
    [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.core.async :refer [chan <! put!]]
    [goog.object :as gobj]
    [om.next :as om]
    [taoensso.timbre :refer [debug]]))

;(defn load-checkout [on-load]
;  (let [channel (chan)]
;    (go (<! channel)
;        (on-load))
;    (-> (goog.net.jsloader.load "https://checkout.stripe.com/v2/checkout.js")
;        (.addCallback #(put! channel [:stripe-checkout-loaded :success])))))

;(defn stripe-token-recieved-cb [component]
;  (fn [token]
;    (let [clj-token (js->clj token)]
;      (debug "Recieved token from Stripe.")
;      ;(trace "Recieved token from Stripe: " clj-token)
;      (om/transact! component `[(stripe/update-card ~{:token clj-token})
;                                :query/stripe]))))
;(defn checkout-loaded? []
;  (boolean (gobj/get js/window "StripeCheckout")))

;(defn open-checkout [component email]
;  (let [checkout (.configure js/StripeCheckout
;                             (clj->js {:key    "pk_test_VhkTdX6J9LXMyp5nqIqUTemM"
;                                       :locale "auto"
;                                       :token  (stripe-token-recieved-cb component)}))]
;    (.open checkout
;           #js {:name            "SULO"
;                :email           email
;                :locale          "auto"
;                :allowRememberMe false
;                :opened          #(debug " StripeCheckout did open") ; #(.show-loading component false)
;                :closed          #(debug "StripeCheckout did close.")
;                :panelLabel      ""
;                })))

(defn create-token [card on-success on-error]
  (let [stripe (js/Stripe "pk_test_VhkTdX6J9LXMyp5nqIqUTemM")]
    (.. stripe
        (createToken card)
        (then (fn [res]
                (if (.-error res)
                  (on-error (.-error res))
                  (on-success (.-token res))))))))

(defn mount-payment-form [{:keys [element-id]}]
  (debug "Mount stripe")
  (let [elements (.elements (js/Stripe "pk_test_VhkTdX6J9LXMyp5nqIqUTemM"))
        card (.create elements "card" (clj->js {:style {:base {:color      "#32325d"
                                                               :fontSmoothing "antialiased"
                                                               :lineHeight "24px"
                                                               :fontSize   "16px"
                                                               "::placeholder" {:color "#aab7c4"}}}}))]
    (.mount card (str "#" element-id))
    card))