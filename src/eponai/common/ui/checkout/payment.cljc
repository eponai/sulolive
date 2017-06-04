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
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.common :as common]
    [eponai.web.ui.button :as button]))

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
             ret {:source         source
                  :card           card
                  :is-new-source? true}]
         (debug "Payment Ret : " ret)
         ret))))

(defn render-payment [this props state]
  (let [{:keys [payment-error card add-new-card? selected-source]} state
        {:keys [error amount sources]} props]
    (debug "Selected source: " selected-source)
    (dom/div
      (css/add-class :checkout-payment)
      ;(when (not-empty sources)
      ;  [
      ;   (dom/div (css/add-class :section-title)
      ;            (dom/span nil "Saved cards"))])
      (menu/vertical
        (css/add-classes [:section-list :section-list--cards])
        (map-indexed (fn [i c]
                       (let [{:stripe.card/keys [brand last4 id]} c]
                         (menu/item
                           (css/add-class :section-list-item--card)
                           (dom/a
                             {:onClick #(om/update-state! this assoc :selected-source id)}
                             (dom/input {:type    "radio"
                                         :name    "sulo-select-cc"
                                         :checked (= selected-source id)})
                             (dom/div
                               (css/add-class :payment-card)
                               (dom/div {:classes ["icon" (get {"Visa"             "icon-cc-visa"
                                                                "American Express" "icon-cc-amex"
                                                                "MasterCard"       "icon-cc-mastercard"
                                                                "Discover"         "icon-cc-discover"
                                                                "JCB"              "icon-cc-jcb"
                                                                "Diners Club"      "icon-cc-diners"
                                                                "Unknown"          "icon-cc-unknown"} brand "icon-cc-unknown ")]})
                               (dom/p nil
                                      (dom/span (css/add-class :payment-brand) brand)
                                      (dom/small (css/add-class :payment-last4) (str "ending in " last4))
                                      )))
                           )))
                     sources)

        (menu/item
          (css/add-classes [:section-list-item--new-card])
          (dom/a
            (cond->> {:onClick #(om/update-state! this assoc :selected-source :new-card)}
                     (and (not-empty sources)
                          (not add-new-card?))
                     (css/add-class :hide))
            (dom/input
              (cond->> {:type    "radio"
                        :name    "sulo-select-cc"
                        :checked (= selected-source :new-card)}
                       (and (not-empty sources)
                            (not add-new-card?))
                       (css/add-class :hide)))
            (dom/div
              (cond->> {:id stripe-card-element}
                       (and (not-empty sources)
                            (not add-new-card?))
                       (css/add-class :hide))))
          (if (and (not-empty sources)
                     (not add-new-card?))
            (button/user-setting-default
              {:onClick #(om/update-state! this assoc :add-new-card? true :selected-source :new-card)}
              (dom/span nil "Add new card...")))))

      ;(when (and (not-empty sources)
      ;           (not add-new-card?))
      ;  (dom/div
      ;    nil
      ;    (dom/a (->> (css/button-hollow {:onClick #(om/update-state! this assoc :add-new-card? true)})
      ;                (css/add-class :secondary)
      ;                (css/add-class :small))
      ;           (dom/span nil "Add new card"))))

      (dom/div
        (css/text-align :center {:id "card-errors"})
        (dom/small nil (or error payment-error)))
      (dom/div (css/text-align :right)
               (dom/a
                 (css/button (when-not (script-loader/is-loading-scripts? this)
                               {:onClick #(.save-payment this)}))
                 (dom/span nil "Complete purchase"))
               (dom/p nil
                      (dom/small nil "This sale will be processed as ")
                      (dom/small nil (two-decimal-price amount))
                      (dom/small nil " Canadian Dollars."))))))

(defui CheckoutPayment-no-loader
  static script-loader/IRenderLoadingScripts
  (render-while-loading-scripts [this props]
    (render-payment this props nil))
  Object
  #?(:cljs
     (save-payment
       [this]
       (let [{:keys [card stripe selected-source]} (om/get-state this)
             {:keys [on-change on-error]} (om/get-computed this)
             on-success (fn [token]
                          (when on-change
                            (on-change (token->payment token))))
             on-error (fn [^js/Stripe.card.createToken.Reponse.Error error]
                        (om/update-state! this assoc :payment-error (.-message error))
                        (when on-error
                          (on-error (.-message error))))
             ^js/Stripe stripe stripe]
         (if (= selected-source :new-card)
           (.. stripe
               (createToken card)
               (then (fn [^js/Stripe.card.createToken.Response res]
                       (if (.-error res)
                         (on-error (.-error res))
                         (on-success (.-token res))))))
           (when on-change
             (on-change {:source selected-source}))))))

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
  (initLocalState [this]
    (let [{:keys [default-source]} (om/props this)]
      {:selected-source (or default-source :new-card)}))

  (render [this]
    (render-payment this (om/props this) (om/get-state this))))

(def CheckoutPayment (script-loader/stripe-loader CheckoutPayment-no-loader))

(def ->CheckoutPayment (om/factory CheckoutPayment))
