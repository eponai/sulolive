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
    [taoensso.timbre :refer [debug]]
    [eponai.client.parser.message :as msg]
    [eponai.common :as c]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.menu :as menu]
    [clojure.string :as string]))

(defn get-route-params [component]
  (get-in (om/props component) [:query/current-route :route-params]))

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
                                                           {:store/profile [:store.profile/name
                                                                            {:store.profile/photo [:photo/id]}]}]}]}
                       ]}
     {:query/stripe-customer [:stripe/id
                              :stripe/sources]}
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
             {:checkout/keys [shipping]} (om/get-state this)
             {:keys [source]} payment
             {:keys [route-params]} current-route
             {:keys [store-id]} route-params]
         (let [items checkout]
           (msg/om-transact! this `[(store/create-order ~{:order    {
                                                                     ;:customer     (:stripe/id stripe-customer)
                                                                     :source       source
                                                                     :shipping     shipping
                                                                     :items        items
                                                                     :shipping-fee 5
                                                                     :subtotal     (review/compute-item-price items)}
                                                          :store-id (c/parse-long store-id)})])))))
  (save-payment [this]
    #?(:cljs
       (let [{:checkout/keys [payment]} (om/get-state this)
             {:keys [source is-new-source?]} payment]
         (if is-new-source?
           (msg/om-transact! this [(list 'stripe/update-customer {:source source})])
           (.place-order this payment)))))

  (initLocalState [_]
    {:checkout/shipping nil
     :checkout/payment  nil
     :open-section      :shipping})

  (componentDidUpdate [this _ _]
    (when-let [customer-response (msg/last-message this 'stripe/update-customer)]
      (debug "Saved customer: " customer-response)
      (when (and (msg/final? customer-response)
                 (msg/success? customer-response))
        (let [message (msg/message customer-response)]
          (msg/clear-messages! this 'stripe/update-customer)
          (.place-order this {:source (:id (:new-card message))}))))

    (when-let [response (msg/last-message this 'store/create-order)]
      (debug "Created order: " response)
      (when (msg/final? response)
        (let [message (msg/message response)]
          (debug "Message: " message)
          (msg/clear-messages! this 'store/create-order)
          (if (msg/success? response)
            (let [{:query/keys [auth]} (om/props this)]
              (debug "Will re-route to " (routes/url :user/order {:order-id (:db/id message) :user-id (:db/id auth)}))
              ;(routes/set-url! this :user/order {:order-id (:db/id message) :user-id (:db/id auth)})
              )
            (om/update-state! this assoc :error-message message))))))

  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [checkout current-route stripe-customer]} (om/props this)
          {:checkout/keys [shipping payment]
           :keys          [open-section error-message]} (om/get-state this)
          {:keys [route]} current-route
          checkout-resp (msg/last-message this 'store/create-order)
          subtotal (review/compute-item-price checkout)
          shipping-fee 5
          grandtotal (+ subtotal shipping-fee)]

      (debug "Stripe customer checkout : " stripe-customer)

      (common/page-container
        {:navbar navbar :id "sulo-checkout"}
        (when (msg/pending? checkout-resp)
          (common/loading-spinner nil))
        (grid/row
          (css/align :center)
          (grid/column
            (grid/column-size {:small 12 :medium 8 :large 8})
            (dom/div
              nil
              (review/->CheckoutReview {:items    checkout
                                        :subtotal subtotal
                                        :shipping shipping-fee}))

            (ship/->CheckoutShipping (om/computed {:collapse? (not= open-section :shipping)
                                                   :shipping  shipping}
                                                  {:on-change #(om/update-state! this assoc :checkout/shipping % :open-section :payment)
                                                   :on-open   #(om/update-state! this assoc :open-section :shipping)}))

            (callout/callout
              nil
              (dom/div
                (css/add-class :section-title)
                (dom/p nil "2. Payment"))
              (dom/div
                (when (not= open-section :payment)
                  (css/add-class :hide))
                (pay/->CheckoutPayment (om/computed {:error   error-message
                                                     :amount  grandtotal
                                                     :sources (:stripe/sources stripe-customer)}
                                                    {:on-change #(do
                                                                  (debug "Got payment: " %)
                                                                  (om/update-state! this assoc :checkout/payment %)
                                                                  (.save-payment this))}))))))))))

(def ->Checkout (om/factory Checkout))

(router/register-component :checkout Checkout)