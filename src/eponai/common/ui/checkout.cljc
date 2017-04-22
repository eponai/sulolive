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


(defn subway-stop-dot [is-checked?]
  (dom/div
    (css/add-class :sl-subway-stop-dot)
    (if is-checked?
      (dom/i {:classes ["fa fa-check-circle fa-fw"]})
      (dom/i {:classes ["fa fa-circle-o fa-fw"]}))))

(defui Checkout
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/cart [{:cart/items [:db/id
                                 :store.item.sku/uuid
                                 :store.item.sku/variation
                                 {:store.item/_skus [:store.item/price
                                                     {:store.item/photos [:photo/path]}
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
     :checkout/payment  nil})

  (componentDidUpdate [this _ _]
    (when-let [response (msg/last-message this 'store/create-order)]
      (debug "Response: " response)
      (if (msg/final? response)
        (let [message (msg/message response)
              {:query/keys [auth]} (om/props this)]
          (debug "message: " message)
          (msg/clear-messages! this 'store/create-order)
          (routes/set-url! this :user/order {:order-id (:db/id message) :user-id (:db/id auth)})))))

  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [cart current-route]} (om/props this)
          {:checkout/keys [shipping payment]} (om/get-state this)
          {:keys [route] } current-route
          progress (cond (nil? shipping) 1
                         (nil? payment) 2
                         :else 3)
          checkout-resp (msg/last-message this 'store/create-order)]

      (common/page-container
        {:navbar navbar :id "sulo-checkout"}
        (when (msg/pending? checkout-resp)
          (common/loading-spinner nil))
        (grid/row-column
          nil
          (dom/ul
            (css/add-class :sl-subway)
            (dom/li
              (cond->> (css/add-class :sl-subway-stop)
                       (some? shipping)
                       (css/add-class ::css/is-active))
              (dom/a
                nil
                (subway-stop-dot (some? shipping))
                (dom/div nil
                         (dom/span nil "Ship to"))))
            (dom/li
              (cond->> (css/add-class :sl-subway-stop)
                      (some? payment)
                      (css/add-class ::css/is-active))
              (dom/a
                nil
                (subway-stop-dot (some? payment))
                (dom/div nil (dom/span nil "Payment"))))
            (dom/li
              (cond->> (css/add-class :sl-subway-stop)
                      (= route :checkout/review)
                      (css/add-class ::css/is-active))
              (dom/a
                nil
                (subway-stop-dot false)
                (dom/div nil (dom/span nil "Review & confirm"))))))

        (grid/row
          (css/align :center)
          (grid/column
            (grid/column-size {:small 12 :medium 8 :large 8})
            (dom/div
              (when-not (= 1 progress)
                (css/add-class :hide))
              (ship/->CheckoutShipping (om/computed {}
                                                    {:on-change #(om/update-state! this assoc :checkout/shipping %)})))
            (dom/div
              (when-not (= 2 progress)
                (css/add-class :hide))
              (pay/->CheckoutPayment (om/computed {}
                                                  {:on-change #(om/update-state! this assoc :checkout/payment %)})))
            (dom/div
              (when-not (= 3 progress)
                (css/add-class :hide))
              (review/->CheckoutReview (om/computed {}
                                                    {:on-confirm        #(.place-order this)
                                                     :checkout/payment  payment
                                                     :checkout/shipping shipping
                                                     :checkout/items    (:cart/items cart)})))))))))

(def ->Checkout (om/factory Checkout))