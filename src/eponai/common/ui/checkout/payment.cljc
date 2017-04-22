(ns eponai.common.ui.checkout.payment
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    #?(:cljs
       [eponai.web.utils :as utils])
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.grid :as grid]))

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

(defui CheckoutPayment
  Object
  #?(:cljs
     (save-payment
       [this]
       (let [{:keys [card stripe]} (om/get-state this)
             {:keys [on-change]} (om/get-computed this)
             on-success (fn [token]
                          (when on-change
                            (on-change (token->payment token))))
             on-error (fn [^js/Stripe.card.createToken.Reponse.Error error]
                        (om/update-state! this assoc :payment-error (.-message error)))
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
    (let [{:keys [payment-error card]} (om/get-state this)
          {:keys [collapse? error]} (om/props this)]
      (dom/div nil
        (my-dom/div
          (css/add-class ::css/callout)
          (dom/h3 nil "Payment")
          (dom/hr nil)
          (my-dom/div
            (when collapse?
              (css/add-class :hide))
            (grid/row
              nil
              (my-dom/div
                (->> (css/grid-column))
                (dom/label #js {:htmlFor "sulo-card-element"} "Card")
                (dom/div #js {:id "sulo-card-element"})
                (dom/div #js {:id        "card-errors"
                              :className "text-center"}
                  (dom/small nil (or error payment-error)))))
            (my-dom/div (css/text-align :right)
                        (dom/a #js {:className "button"
                                    :onClick   #(.save-payment this)}
                               "Complete purchase"))))))))

(def ->CheckoutPayment (om/factory CheckoutPayment))
