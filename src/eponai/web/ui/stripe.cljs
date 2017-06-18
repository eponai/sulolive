(ns eponai.web.ui.stripe
  (:require
    [eponai.common.shared :as shared]
    [eponai.common.database :as db]
    [taoensso.timbre :refer [debug]]))

(defprotocol IStripeClient
  (bank-account [this params callbacks])
  (source-card [this card callbacks])
  (card-element [this element-id])
  ;; marked somewhat private, because we hopefully don't need it outside this ns.
  (-stripe-instance [this]))

(defn stripe-client [stripe-key]
  (.setPublishableKey js/Stripe stripe-key)
  (let [^js/Stripe stripe (js/Stripe stripe-key)]
    (reify IStripeClient
      (bank-account [this params {:keys [on-success on-error]}]
        (.createToken js/Stripe.bankAccount
                      (clj->js params)
                      (fn [status ^js/Stripe.createToken.Response response]
                        (cond (= status 200)
                              (when on-success
                                (on-success {:token (.-id response)
                                             :ip    (.-client_ip response)}))
                              (some? (.-error response))
                              (when on-error
                                (on-error (.-message (.-error response))))))))
      (source-card [this card {:keys [on-success on-error]}]
        (.. stripe
            (createToken ^js/Stripe.card card)
            (then (fn [^js/Stripe.card.createToken.Response res]
                    (if (.-error res)
                      (let [^js/Stripe.card.createToken.Reponse.Error error (.-error res)]
                        (on-error (.-message error)))
                      (on-success (.-token res)))))))
      (card-element [this element-id]
        (let [elements (.elements stripe)
              card (.create ^js/Stripe.elements elements "card"
                            (clj->js {:style {:base {:color          "#32325d"
                                                     :fontSmoothing  "antialiased"
                                                     :lineHeight     "24px"
                                                     :fontSize       "16px"
                                                     "::placeholder" {:color "#aab7c4"}}}}))]
          (.mount ^js/Stripe.card card element-id)
          card))
      (-stripe-instance [this]
        stripe))))

(defn token->card [token]
  (when token
    (let [^js/Stripe.card.Token token token
          card (js->clj (.-card token) :keywordize-keys true)
          source (.-id token)]
      {:source         source
       :card           card
       :is-new-source? true})))

;(defn mount-card [card element-id]
;  (.mount ^js/Stripe.card card element-id))

(defmethod shared/shared-component [:shared/stripe ::shared/client-env]
  [reconciler _ _]
  (let [{:keys [stripe-publishable-key]} (db/singleton-value (db/to-db reconciler)
                                                             :ui.singleton.client-env/env-map)]
    (debug "Using stripe-publishable-key: " stripe-publishable-key)
    (stripe-client stripe-publishable-key)))
