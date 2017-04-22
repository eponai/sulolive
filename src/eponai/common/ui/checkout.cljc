(ns eponai.common.ui.checkout
  (:require
    #?(:cljs
       [eponai.common.ui.checkout.google-places :as places])
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
    [taoensso.timbre :refer [debug]]
    [eponai.client.parser.message :as msg]
    [eponai.common :as c]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.menu :as menu]))

(defn get-route-params [component]
  (get-in (om/props component) [:query/current-route :route-params]))

(defui Checkout
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/cart [{:cart/items [:db/id
                                 :store.item.sku/uuid
                                 :store.item.sku/variation
                                 {:store.item/_skus [:store.item/price
                                                     {:store.item/photos [:store.item.photo/index
                                                                          {:store.item.photo/photo [:photo/path]}]}
                                                     :store.item/name
                                                     {:store/_items [:db/id
                                                                     :store/name
                                                                     {:store/photo [:photo/path]}]}]}]}]}
     :query/current-route
     {:query/auth [:db/id
                   :user/email]}
     :query/messages])
  Object
  #?(:cljs
     (place-order
       [this]
       (let [{:query/keys [current-route cart]} (om/props this)
             {:checkout/keys [shipping payment]} (om/get-state this)
             {:keys [source]} payment
             {:keys [route-params]} current-route
             {:keys [store-id]} route-params]
         (let [items (filter #(= (c/parse-long store-id) (get-in % [:store.item/_skus :store/_items :db/id])) (:cart/items cart))]
           (msg/om-transact! this `[(store/create-order ~{:order    {:source   source
                                                                     :shipping shipping
                                                                     :items    items}
                                                          :store-id (c/parse-long store-id)})])))))

  (initLocalState [_]
    {:checkout/shipping nil
     :checkout/payment  nil
     :open-section :shipping})

  (componentDidUpdate [this _ _]
    (when-let [response (msg/last-message this 'store/create-order)]
      (debug "Response: " response)
      (when (msg/final? response)
        (let [message (msg/message response)]
          (debug "Message: " message)
          (msg/clear-messages! this 'store/create-order)
          (if (msg/success? response)
            (let [{:query/keys [auth]} (om/props this)]
              (routes/set-url! this :user/order {:order-id (:db/id message) :user-id (:db/id auth)}))
            (om/update-state! this assoc :error-message message))))))

  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [cart current-route]} (om/props this)
          {:checkout/keys [shipping payment]
           :keys [open-section error-message]} (om/get-state this)
          {:keys [route] } current-route
          checkout-resp (msg/last-message this 'store/create-order)]

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
              (review/->CheckoutReview (om/computed {}
                                                    {:on-confirm        #(.place-order this)
                                                     :checkout/payment  payment
                                                     :checkout/shipping shipping
                                                     :checkout/items    (:cart/items cart)})))

            (dom/div
              nil
              (ship/->CheckoutShipping (om/computed {:collapse? (not= open-section :shipping)
                                                     :shipping shipping}
                                                    {:on-change #(om/update-state! this assoc :checkout/shipping % :open-section :payment)
                                                     :on-open #(om/update-state! this assoc :open-section :shipping)})))
            (dom/div
              nil
              (pay/->CheckoutPayment (om/computed {:collapse? (not= open-section :payment)
                                                   :error error-message}
                                                  {:on-change #(do
                                                                (om/update-state! this assoc :checkout/payment %)
                                                                (.place-order this))})))))))))

(def ->Checkout (om/factory Checkout))