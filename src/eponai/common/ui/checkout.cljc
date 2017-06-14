(ns eponai.common.ui.checkout
  (:require
    [eponai.common.ui.checkout.shipping :as ship]
    [eponai.common.ui.checkout.payment :as pay]
    [eponai.common.ui.checkout.review :as review]
    [eponai.common.ui.dom :as dom]
    [eponai.client.routes :as routes]
    [om.next :as om :refer [defui]]
    #?(:cljs [eponai.web.utils :as web-utils])
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.router :as router]
    [eponai.common.ui.utils :as ui-utils]
    [taoensso.timbre :refer [debug]]
    [eponai.client.parser.message :as msg]
    [eponai.common :as c]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.menu :as menu]
    [clojure.string :as string]))

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
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/checkout [:db/id
                       {:user.cart/_items [:user/_cart]}
                       :store.item.sku/variation
                       :store.item.sku/inventory
                       {:store.item/_skus [:store.item/price
                                           {:store.item/photos [:store.item.photo/index
                                                                {:store.item.photo/photo [:photo/id]}]}
                                           :store.item/name
                                           {:store/_items [:db/id
                                                           {:store/shipping [{:shipping/rules [:shipping.rule/rates
                                                                                               {:shipping.rule/destinations [:country/code]}]}]}
                                                           {:store/profile [:store.profile/name
                                                                            ;:store.profile/shipping-fee
                                                                            {:store.profile/photo [:photo/id]}]}]}]}
                       ]}
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
              :shipping/keys [selected-rate]} (om/get-state this)
             shipping-fee (compute-shipping-fee selected-rate checkout)
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
                                                                     :shipping-rate {:amount      shipping-fee
                                                                                     :title       (:shipping.rate/title selected-rate)
                                                                                     :description (:shipping.rate/info selected-rate)}
                                                                     :subtotal      (review/compute-item-price items)}
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
    (om/update-state! this merge (.state-from-shipping this (om/props this) shipping)))

  (state-from-shipping [_ props shipping]
    (let [country-code (get-in shipping [:shipping/address :shipping.address/country])
          {:query/keys [checkout]} props
          store (:store/_items (:store.item/_skus (first checkout)))
          shipping-rules (get-in store [:store/shipping :shipping/rules])
          rule-for-country (some (fn [r] (some #(when (= (:country/code %) country-code) r) (:shipping.rule/destinations r))) shipping-rules)
          available-rates (map #(assoc % :shipping.rate/total (compute-shipping-fee % checkout)) (:shipping.rule/rates rule-for-country))
          new-selected-rate (first (sort-by :shipping.rate/total available-rates))]
      (debug "State from shipping: " shipping)
      {:checkout/shipping        shipping
       :open-section             (if (and (some? shipping) (not-empty available-rates)) :payment :shipping)
       :shipping/available-rates available-rates
       :shipping/selected-rate   new-selected-rate}))

  (local-state-from-props [this props]
    (let [{:query/keys [stripe-customer]} props]
      (if (some? (:stripe/shipping stripe-customer))
        (let [address (:stripe.shipping/address (:stripe/shipping stripe-customer))
              formatted {:shipping/name    (:stripe.shipping/name (:stripe/shipping stripe-customer))
                         :shipping/address {:shipping.address/street   (:stripe.shipping.address/street address)
                                            :shipping.address/street2  (:stripe.shipping.address/street2 address)
                                            :shipping.address/locality (:stripe.shipping.address/city address)
                                            :shipping.address/country  (:stripe.shipping.address/country address)
                                            :shipping.address/region   (:stripe.shipping.address/state address)
                                            :shipping.address/postal   (:stripe.shipping.address/postal address)}}]
          (.state-from-shipping this props formatted))
        (.state-from-shipping this props nil))))

  ;; React lifecycle
  (initLocalState [this]
    (merge {:checkout/payment nil}
           (.local-state-from-props this (om/props this))))

  (componentWillReceiveProps [this next-props]
    (when-not (= (:query/stripe-customer next-props)
                 (:query/stripe-customer (om/props this)))
      (om/update-state! this merge (.local-state-from-props this next-props))))

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
    (let [{:proxy/keys [navbar]
           :query/keys [checkout current-route stripe-customer countries]} (om/props this)
          {:checkout/keys [shipping payment pending-payment]
           :keys          [open-section error-message]
           :shipping/keys [available-rates selected-rate]} (om/get-state this)
          {:keys [route]} current-route
          checkout-resp (msg/last-message this 'store/create-order)
          customer-resp (msg/last-message this 'stripe/update-customer)
          subtotal (review/compute-item-price checkout)
          shipping-fee (compute-shipping-fee selected-rate checkout)
          grandtotal (+ subtotal shipping-fee)]

      (common/page-container
        {:navbar navbar :id "sulo-checkout"}
        (when-let [loading-message (:loading/message (om/get-state this))]
          (common/loading-spinner nil (dom/span nil loading-message)))

        (grid/row
          (css/align :center)
          (grid/column
            (grid/column-size {:small 12 :medium 8 :large 8})
            (dom/div
              nil
              (review/->CheckoutReview {:items    checkout
                                        :subtotal subtotal
                                        :shipping shipping-fee}))

            (callout/callout
              nil
              (dom/div
                (css/add-class :section-title)
                (dom/p nil "1. Ship to"))
              (ship/->CheckoutShipping (om/computed {:collapse? (not= open-section :shipping)
                                                     :shipping  shipping
                                                     :countries countries}
                                                    {:on-change         #(.save-shipping this %)
                                                     ;:on-country-change #(.save-shipping this {:shipping/address {:shipping.address/country %}})
                                                     :on-open           #(om/update-state! this assoc :open-section :shipping)}))
              (when (and (some? shipping) (empty? available-rates))
                (let [store (:store/_items (:store.item/_skus (first checkout)))]
                  (callout/callout-small
                    (->> (css/add-class :warning)
                         (css/text-align :center))
                    (dom/small nil (str "Sorry, " (get-in store [:store/profile :store.profile/name]) " does not ship to this country."))))))

            (callout/callout
              nil
              (dom/div
                (css/add-class :section-title)
                (dom/p nil "2. Delivery & Payment"))
              (dom/div
                (when (or (not= open-section :payment)
                          (empty? available-rates))
                  (css/add-class :hide))

                (dom/div
                  (css/add-class :subsection)
                  (dom/p (css/add-class :subsection-title) "Delivery options")
                  (menu/vertical
                    (css/add-classes [:section-list :section-list--shipping])
                    (map (fn [r]
                           (let [{:shipping.rate/keys [free-above total info]} r]
                             (menu/item
                               (css/add-class :section-list-item)
                               (dom/a
                                 {:onClick #(om/update-state! this assoc :shipping/selected-rate r)}
                                 (dom/div
                                   (css/add-class :shipping-info)
                                   (dom/input
                                     {:type    "radio"
                                      :name    "sulo-select-rate"
                                      :checked (= selected-rate r)})
                                   (dom/div
                                     (css/add-class :shipping-rule)
                                     (dom/p nil (dom/span nil (:shipping.rate/title r))
                                            (dom/br nil)
                                            (dom/small nil info))))
                                 (dom/div
                                   (css/add-class :shipping-cost)
                                   (dom/p nil
                                          (dom/span nil (if (zero? total)
                                                          "Free"
                                                          (ui-utils/two-decimal-price (:shipping.rate/total r))))
                                          (when (and (some? free-above)
                                                     (< subtotal free-above))
                                            [
                                             (dom/br nil)
                                             (dom/small nil (str "Free for orders above " (ui-utils/two-decimal-price free-above)))])))))))
                         (sort-by :shipping.rate/total available-rates))))

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

(router/register-component :checkout Checkout)