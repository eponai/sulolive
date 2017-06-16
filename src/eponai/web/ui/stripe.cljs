(ns eponai.web.ui.stripe)

(def stripe-key "pk_live_qu5NUdQtDePkNiaeH5EjhEGH")

(defn bank-account [params {:keys [on-success on-error]}]
  (.setPublishableKey js/Stripe stripe-key)
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

(defn instance []
  (js/Stripe stripe-key))

(defn source-card [^js/Stripe stripe ^js/Stripe.card card {:keys [on-success on-error]}]
  (.. stripe
      (createToken card)
      (then (fn [^js/Stripe.card.createToken.Response res]
              (if (.-error res)
                (let [^js/Stripe.card.createToken.Reponse.Error error (.-error res)]
                  (on-error (.-message error)))
                (on-success (.-token res)))))))

(defn card-element [stripe element-id]
  (let [elements (.elements stripe)
        card (.create ^js/Stripe.elements elements "card"
                      (clj->js {:style {:base {:color          "#32325d"
                                               :fontSmoothing  "antialiased"
                                               :lineHeight     "24px"
                                               :fontSize       "16px"
                                               "::placeholder" {:color "#aab7c4"}}}}))]
    (.mount ^js/Stripe.card card element-id)
    card))

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