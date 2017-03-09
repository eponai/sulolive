(ns eponai.common.ui.checkout
  (:require
    #?(:cljs
       [eponai.common.ui.checkout.google-places :as places])
    [eponai.common.ui.checkout.shipping :as ship]
    [eponai.common.ui.checkout.payment :as pay]
    [eponai.common.ui.checkout.review :as review]
    [eponai.common.ui.dom :as my-dom]
    [eponai.client.routes :as routes]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    #?(:cljs [eponai.web.utils :as web-utils])
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]
    [eponai.client.parser.message :as msg]
    [eponai.common :as c]))

(defn get-route-params [component]
  (get-in (om/props component) [:query/current-route :route-params]))

(defui Checkout
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/cart [{:cart/items [:db/id
                                 :store.item.sku/uuid
                                 :store.item.sku/value
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
           (msg/om-transact! this `[(user/checkout ~{:source   source
                                                     :shipping shipping
                                                     :items    (map :store.item.sku/uuid items)
                                                     :store-id (c/parse-long store-id)})])))))

  (initLocalState [_]
    {:checkout/shipping nil
     :checkout/payment  nil})

  (componentDidUpdate [this _ _]
    (when-let [response (msg/last-message this 'user/checkout)]
      (debug "Response: " response)
      (if (msg/final? response)
        (let [message (msg/message response)
              {:query/keys [auth]} (om/props this)]
          (debug "message: " message)
          (msg/clear-messages! this 'user/checkout)
          (routes/set-url! this :user/order {:order-id (:db/id message) :user-id (:db/id auth)})))))

  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [cart current-route]} (om/props this)
          {:checkout/keys [shipping payment]} (om/get-state this)
          progress (cond (nil? shipping) 1
                         (nil? payment) 2
                         :else 3)
          checkout-resp (msg/last-message this 'user/checkout)]

      (common/page-container
        {:navbar navbar :id "sulo-checkout"}
        (when (msg/pending? checkout-resp)
          (common/loading-spinner nil))
        (my-dom/div
          (->> (css/grid-row)
               (css/align :center)
               (css/add-class :collapse))
          (my-dom/div
            (->> (css/grid-column)
                 (css/grid-column-size {:small 12 :medium 8 :large 8}))
            (dom/div #js {:className "progress"}
              (dom/div #js {:className "progress-meter"
                            :style     #js {:width (str (int (* 100 (/ progress 3))) "%")}}))
            (dom/div #js {:className (when-not (= 1 progress) "hide")}
              (ship/->CheckoutShipping (om/computed {}
                                                    {:on-change #(om/update-state! this assoc :checkout/shipping %)})))
            (dom/div #js {:className (when-not (= 2 progress) "hide")}
              (pay/->CheckoutPayment (om/computed {}
                                                  {:on-change #(om/update-state! this assoc :checkout/payment %)})))
            (dom/div #js {:className (when-not (= 3 progress) "hide")}
              (review/->CheckoutReview (om/computed {}
                                                    {:on-confirm        #(.place-order this)
                                                     :checkout/payment  payment
                                                     :checkout/shipping shipping
                                                     :checkout/items    (:cart/items cart)})))))))))

(def ->Checkout (om/factory Checkout))