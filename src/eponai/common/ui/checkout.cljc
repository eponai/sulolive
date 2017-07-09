(ns eponai.common.ui.checkout
  (:require
    [eponai.common.ui.checkout.shipping :as ship]
    [eponai.common.ui.checkout.payment :as pay]
    [eponai.common.ui.checkout.review :as review]
    [eponai.common.ui.dom :as dom]
    [eponai.client.routes :as routes]
    [om.next :as om :refer [defui]]
    #?(:cljs [eponai.web.utils :as web-utils])
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]
    [eponai.client.parser.message :as msg]
    [eponai.common :as c]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.callout :as callout]))

(defn get-route-params [component]
  (get-in (om/props component) [:query/current-route :route-params]))

(defn compute-shipping-fee [rate items]
  (let [{:shipping.rate/keys [additional free-above]
         first-rate          :shipping.rate/first} rate
        subtotal (review/compute-item-price items)
        item-count (count items)]
    (cond (and (some? free-above)
               (> subtotal free-above))
          0

          (<= 1 item-count)
          (apply + (or first-rate 0) (repeat (dec item-count) (or additional 0)))

          :else
          (or first-rate 0))))

(defui Checkout
  static om/IQuery
  (query [_]
    [{:query/checkout [:db/id
                       {:user.cart/_items [:user/_cart]}
                       :store.item.sku/variation
                       :store.item.sku/inventory
                       {:store.item/_skus [:store.item/price
                                           {:store.item/photos [:store.item.photo/index
                                                                {:store.item.photo/photo [:photo/id]}]}
                                           :store.item/name
                                           {:store/_items [:db/id
                                                           {:store/shipping [{:shipping/rules [:shipping.rule/rates
                                                                                               {:shipping.rule/destinations [:country/code]}]}
                                                                             {:shipping/address [:shipping.address/country
                                                                                                 :shipping.address/region
                                                                                                 :shipping.address/locality
                                                                                                 :shipping.address/postal
                                                                                                 :shipping.address/street]}]}
                                                           {:store/profile [:store.profile/name
                                                                            ;:store.profile/shipping-fee
                                                                            {:store.profile/photo [:photo/id]}]}]}]}
                       ]}
     {:query/taxes [:taxes/id
                    :taxes/rate
                    :taxes/freight-taxable?]}
     {:query/countries [:country/code :country/name]}
     {:query/stripe-customer [:stripe/id
                              :stripe/sources
                              :stripe/shipping
                              :stripe/default-source]}
     :query/current-route
     {:query/auth [:db/id
                   :user/email
                   :user/stripe]}
     :query/messages])
  Object
  (place-order
    [this payment]
    #?(:cljs
       (let [{:query/keys [current-route checkout]} (om/props this)
             {:checkout/keys [shipping]
              :shipping/keys [selected-rate]
              :keys          [shipping-fee grandtotal tax-amount subtotal]} (om/get-state this)
             ;shipping-fee (compute-shipping-fee selected-rate checkout)
             ;shipping-fee (get-in (first checkout) [:store.item/_skus :store/_items :store/profile :store.profile/shipping-fee] 0)
             {:keys [source]} payment
             {:keys [route-params]} current-route
             {:keys [store-id]} route-params]
         (let [items checkout]
           (msg/om-transact! this `[(store/create-order ~{:order    {
                                                                     ;:customer     (:stripe/id stripe-customer)
                                                                     :source        source
                                                                     :shipping      shipping
                                                                     :items         items
                                                                     :tax-amount    tax-amount
                                                                     :grandtotal    grandtotal
                                                                     :shipping-rate {:amount      shipping-fee
                                                                                     :title       (:shipping.rate/title selected-rate)
                                                                                     :description (:shipping.rate/info selected-rate)}
                                                                     :subtotal      subtotal}
                                                          :store-id (c/parse-long store-id)})]))
         (om/update-state! this assoc :loading/message "Placing your order..."))))
  (save-payment [this]
    #?(:cljs
       (let [{:checkout/keys [payment]} (om/get-state this)
             {:keys [source is-new-source?]} payment]
         (if is-new-source?
           (do
             (msg/om-transact! this [(list 'stripe/update-customer {:source source})])
             (om/update-state! this assoc :loading/message "Validating card..."))
           (.place-order this payment)))))

  (save-shipping [this shipping]
    (let [new-state (.state-from-shipping this (om/props this) shipping)
          {:keys [shipping-fee subtotal]} new-state]
      (when-not (:shipping/unavailable? new-state)
        (om/transact! (om/get-reconciler this) [(list {:query/taxes
                                                       [:taxes/id :taxes/rate :taxes/freight-taxable?]}
                                                      {:destination shipping
                                                       :subtotal    subtotal
                                                       :shipping    shipping-fee})]))
      (om/update-state! this merge new-state)))

  (payment-amounts-from-shipping-rate [_ props rate]
    (let [{:query/keys [taxes checkout]} props
          {tax-rate    :taxes/rate
           :taxes/keys [freight-taxable?]} taxes
          tax-rate (or tax-rate 0)
          shipping-fee (if rate (compute-shipping-fee rate checkout) 0)
          subtotal (review/compute-item-price checkout)
          tax-amount (if freight-taxable?
                       (* tax-rate (+ subtotal shipping-fee))
                       (* tax-rate subtotal))]
      {:subtotal     subtotal
       :shipping-fee shipping-fee
       :tax-amount   tax-amount
       :grandtotal   (+ subtotal shipping-fee tax-amount)}))

  (state-from-shipping [this props shipping]
    (let [{country-code :country/code} (get-in shipping [:shipping/address :shipping.address/country])
          {:query/keys [checkout taxes]} props
          store (:store/_items (:store.item/_skus (first checkout)))
          shipping-rules (get-in store [:store/shipping :shipping/rules])
          rule-for-country (some (fn [r] (some #(when (= (:country/code %) country-code) r) (:shipping.rule/destinations r))) shipping-rules)
          available-rates (map #(assoc % :shipping.rate/total (compute-shipping-fee % checkout)) (:shipping.rule/rates rule-for-country))
          new-selected-rate (first (sort-by :shipping.rate/total available-rates))

          allow-pickup? (some #(:shipping.rule/pickup? %) shipping-rules)]
      (debug "State from shipping: " shipping)
      (debug "State from props: " props)
      (merge
        {:checkout/shipping        shipping
         :open-section             (if (and (some? shipping) (not-empty available-rates)) :payment :shipping)
         :shipping/available-rates available-rates
         :shipping/allow-pickup?   allow-pickup?
         :shipping/selected-rate   new-selected-rate
         :shipping/unavailable?    (and (some? shipping) (empty? available-rates))}
        (.payment-amounts-from-shipping-rate this props new-selected-rate))))

  (select-shipping-rate [this rate]
    (om/update-state! this (fn [s]
                             (-> s
                                 (merge (.payment-amounts-from-shipping-rate this (om/props this) rate))
                                 (assoc :shipping/selected-rate rate)))))

  ;; React lifecycle
  (initLocalState [this]
    (let [{:query/keys [stripe-customer]} (om/props this)]
      (merge {:checkout/payment nil}
             (.state-from-shipping this (om/props this) (:stripe/shipping stripe-customer)))))

  (componentWillReceiveProps [this next-props]
    (let [updated-shipping? (not= (:query/stripe-customer next-props)
                                  (:query/stripe-customer (om/props this)))
          updated-taxes? (not= (:query/taxes next-props)
                               (:query/taxes (om/props this)))]
      (when (or updated-taxes? updated-shipping?)
        (let [shipping (if updated-shipping?
                         (get-in next-props [:query/stripe-customer :stripe/shipping])
                         (:checkout/shipping (om/get-state this)))]
          (om/update-state! this merge (.state-from-shipping this next-props shipping)))))

    ;(not= (:query/taxes next-props)
    ;      (:query/taxes (om/props this)))
    ;(om/update-state! this merge (.local-state-from-props this next-props))
    )

  (componentDidUpdate [this _ _]

    ;; Check response for creating a new card on the customer
    ;; (this will only happen if the user added a new card when checking out)
    (when-let [customer-response (msg/last-message this 'stripe/update-customer)]
      (when (msg/final? customer-response)
        (let [message (msg/message customer-response)]
          (msg/clear-messages! this 'stripe/update-customer)

          ;; Response success if the card is valid and added to the customer's list of cards
          (if (msg/success? customer-response)
            ;; If we had a pending payment already, that means we've already added a new card and tried
            ;; to charge it by placing an order. However, it was declined for some reason and has been
            ;; successfully removed from the customer again. (we do this so the user doesn't end up with
            ;; many added cards that didn't actually work)
            (if (some? (:checkout/pending-payment (om/get-state this)))
              (om/update-state! this dissoc :checkout/pending-payment :loading/message)
              ;; The newly created card was successfully created and added to the customer, let's place the order and charge the card.
              (let [pending-payment {:source (:id (:new-card message))}]
                ;; We created a new card, so let's add it to pending to make sure it's successfully charged.
                ;; If it's not, we'll remove it from the customer again.
                (om/update-state! this assoc :checkout/pending-payment pending-payment)
                (.place-order this pending-payment)))

            ;; If card couldn't be added, (invalid or other error) show user what's wrong.
            (om/update-state! this assoc :error-message message :loading/message nil)))))

    ;; Checked response from placing an order.
    (when-let [response (msg/last-message this 'store/create-order)]
      (when (msg/final? response)
        (let [message (msg/message response)]
          (msg/clear-messages! this 'store/create-order)
          (if (msg/success? response)
            ;; Successful order will re-route to user's orders.
            (let [{:query/keys [auth]} (om/props this)]
              (routes/set-url! this :user/order {:order-id (:db/id message) :user-id (:db/id auth)}))
            ;; If order was unsuccessful, let's get the pending payment we added above and remove it from the customer.
            (let [{:checkout/keys [pending-payment]} (om/get-state this)]
              (msg/om-transact! this [(list 'stripe/update-customer {:remove-source (:source pending-payment)})])
              (om/update-state! this assoc :error-message message :loading/message nil)))))))

  (render [this]
    (let [{:query/keys [checkout current-route stripe-customer countries]} (om/props this)
          {:checkout/keys [shipping]
           :keys          [allow-pickup? open-section error-message subtotal shipping-fee tax-amount grandtotal]
           :shipping/keys [available-rates selected-rate]} (om/get-state this)]
      (debug "Checkout props: " (om/props this))
      (debug "Checout state" (om/get-state this))

      (dom/div
        {:id "sulo-checkout"}
        (when-let [loading-message (:loading/message (om/get-state this))]
          (common/loading-spinner nil (dom/span nil loading-message)))

        (dom/h1 (css/show-for-sr) "Checkout")

        (grid/row
          (css/align :center)

          (grid/column
            (grid/column-size {:small 12 :medium 8 :large 8})
            (dom/div
              nil
              (dom/h2 (css/show-for-sr) "Review order")
              (review/->CheckoutReview {:items      checkout
                                        :subtotal   subtotal
                                        :shipping   shipping-fee
                                        :tax-amount tax-amount
                                        :grandtotal grandtotal}))

            (dom/h2 (css/show-for-sr) "Order info")
            (callout/callout
              nil
              (dom/div
                (css/text-align :center)
                (dom/h3 nil "1. Ship to"))
              (ship/->CheckoutShipping (om/computed {:collapse?       (not= open-section :shipping)
                                                     :new-shipping?   (nil? (:stripe/shipping stripe-customer))
                                                     :shipping        shipping
                                                     :countries       countries
                                                     :available-rates available-rates
                                                     :selected-rate   selected-rate
                                                     :subtotal        subtotal
                                                     :allow-pickup? allow-pickup?
                                                     :store           (:store/_items (:store.item/_skus (first checkout)))}
                                                    {:on-save-shipping      #(.save-shipping this %)
                                                     :on-save-shipping-rate #(.select-shipping-rate this %)
                                                     :on-save-country       #(let [new-shipping (assoc-in shipping [:shipping/address :shipping.address/country]
                                                                                                          {:country/code %})]
                                                                              (debug "New shipping: " new-shipping)
                                                                              (om/update-state! this merge
                                                                                                (.state-from-shipping this (om/props this) new-shipping)
                                                                                                {:open-section :shipping}))
                                                     ;:on-country-change #(.save-shipping this {:shipping/address {:shipping.address/country %}})
                                                     :on-open               #(om/update-state! this assoc :open-section :shipping)})))

            (callout/callout
              nil
              (dom/div
                (css/text-align :center)
                (dom/h3 nil "2. Payment"))

              (dom/div
                (when (or (not= open-section :payment)
                          (empty? available-rates))
                  (css/add-class :hide))

                (dom/div
                  (css/add-class :subsection)
                  (dom/p (css/add-class :subsection-title) "Payment options")
                  (pay/->CheckoutPayment (om/computed {:error          error-message
                                                       :amount         grandtotal
                                                       :default-source (:stripe/default-source stripe-customer)
                                                       :sources        (:stripe/sources stripe-customer)}
                                                      {:on-change #(do
                                                                    (debug "Got payment: " %)
                                                                    (om/update-state! this assoc :checkout/payment %)
                                                                    (.save-payment this))})))))))))))

(def ->Checkout (om/factory Checkout))

