(ns eponai.common.ui.checkout.payment
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [om.next :as om :refer [defui]]
    #?(:cljs
       [eponai.web.utils :as utils])
    [eponai.common.ui.utils :refer [two-decimal-price]]
    [eponai.common.ui.script-loader :as script-loader]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.callout :as callout]))

(def stripe-key "pk_test_VhkTdX6J9LXMyp5nqIqUTemM")
(def stripe-card-element "sulo-card-element")

(defn create-token [card on-success on-error]
  #?(:cljs
     (let [stripe (js/Stripe "pk_test_VhkTdX6J9LXMyp5nqIqUTemM")]
       (.. stripe
           (createToken card)
           (then (fn [^js/Stripe.card.createToken.Response res]
                   (if (.-error res)
                     (on-error (.-error res))
                     (on-success (.-token res)))))))))

(defn token->payment [token]
  #?(:cljs
     (when token
       (let [^js/Stripe.card.Token token token
             card (js->clj (.-card token) :keywordize-keys true)
             source (.-id token)
             ret {:source source
                  :card   card}]
         (debug "Payment Ret : " ret)
         ret))))

(defn render-payment [this props state]
  (let [{:keys [payment-error card]} state
        {:keys [error amount]} props]
    (dom/div
      (css/add-class :checkout-payment)
      (dom/label {:htmlFor "sulo-card-element"} "Card")
      (dom/div {:id "sulo-card-element"})
      (dom/div
        (css/text-align :center {:id "card-errors"})
        (dom/small nil (or error payment-error)))
      (dom/div (css/text-align :right)
               (dom/a
                 (->> (css/button (when-not (script-loader/is-loading-scripts? this)
                                    {:onClick #(.save-payment this)}))
                      (css/add-class :disabled))
                 (dom/span nil "Complete purchase"))
               (dom/p nil
                      (dom/small nil "This sale will be processed as ")
                      (dom/small nil (two-decimal-price amount))
                      (dom/small nil " US dollars."))
               )
      (callout/callout-small
        (css/add-class :warning)
        (dom/small nil "Purchases are disabled until we finish work on the payment integration. Hang tight, we're almost there!")))))

(defui CheckoutPayment-no-loader
  static script-loader/IRenderLoadingScripts
  (render-while-loading-scripts [this props]
    (render-payment this props nil))
  Object
  #?(:cljs
     (save-payment
       [this]
       (let [{:keys [card stripe]} (om/get-state this)
             {:keys [on-change on-error]} (om/get-computed this)
             on-success (fn [token]
                          (when on-change
                            (on-change (token->payment token))))
             on-error (fn [^js/Stripe.card.createToken.Reponse.Error error]
                        (om/update-state! this assoc :payment-error (.-message error))
                        (when on-error
                          (on-error (.-message error))))
             ^js/Stripe stripe stripe]
         (.. stripe
             (createToken card)
             (then (fn [^js/Stripe.card.createToken.Response res]
                     (if (.-error res)
                       (on-error (.-error res))
                       (on-success (.-token res)))))))))

  (componentDidMount [this]
    (debug "Stripe component did mount")
    #?(:cljs
       (when (utils/element-by-id stripe-card-element)
         (let [stripe (js/Stripe stripe-key)
               elements (.elements stripe)
               card (.create ^js/Stripe.elements elements "card"
                             (clj->js {:style {:base {:color          "#32325d"
                                                      :fontSmoothing  "antialiased"
                                                      :lineHeight     "24px"
                                                      :fontSize       "16px"
                                                      "::placeholder" {:color "#aab7c4"}}}}))]
           (.mount ^js/Stripe.card card (str "#" stripe-card-element))
           (om/update-state! this assoc :card card :stripe stripe)))))

  (render [this]
    (render-payment this (om/props this) (om/get-state this))))

(def CheckoutPayment (script-loader/stripe-loader CheckoutPayment-no-loader))

(def ->CheckoutPayment (om/factory CheckoutPayment))
