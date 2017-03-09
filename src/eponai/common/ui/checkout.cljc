(ns eponai.common.ui.checkout
  (:require
    #?(:cljs
       [eponai.common.ui.checkout.google-places :as places])
    [eponai.common.ui.checkout.shipping :as ship]
    [eponai.common.ui.checkout.payment :as pay]
    [eponai.common.ui.dom :as my-dom]
    [eponai.client.routes :as routes]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.utils :as utils]
    #?(:cljs [eponai.web.utils :as web-utils])
    ;[eponai.client.routes :as routes]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]
    [eponai.client.parser.message :as msg]
    [eponai.common :as c]))

(defn compute-item-price [items]
  (reduce + (map #(get-in % [:store.item/_skus :store.item/price]) items)))

(defn store-element [s]
  (debug "Store element: " s)
  (let [{:store/keys [photo] store-name :store/name} s]
    (my-dom/div
      (->> (css/grid-row)
           (css/add-class :expanded)
           (css/add-class :store-container)
           (css/align :center)
           )
      (my-dom/div
        (->> (css/grid-column)
             (css/grid-column-size {:small 3 :medium 2}))
        (photo/circle {:src (:photo/path photo)}))
      (my-dom/div
        (->> (css/grid-column)
             (css/grid-column-size {:small 12})
             (css/text-align :center))
        (dom/div nil (dom/p nil (dom/strong #js {:className "store-name"} store-name)))))))

(defn confirm-element [component]
  (let [{:checkout/keys [shipping payment]} (om/get-state component)
        {:keys [card]} payment
        {:query/keys [cart]} (om/props component)
        {:keys [cart/items]} cart
        item-count (count items)]
    (dom/div nil
      (my-dom/div
        (css/grid-row)
        (my-dom/div
          (css/grid-column)
          (dom/h3 nil "Review & Confirm")))
      (dom/div #js {:className "callout"}

        (let [{:address/keys [street1 postal region locality country full-name]} shipping]
          (my-dom/div
            (->> (css/grid-row)
                 (css/align :bottom))
            (my-dom/div
              (->> (css/grid-column)
                   (css/grid-column-size {:small 3 :medium 2}))
              (dom/h4 nil "Ship to")
              (dom/i #js {:className "fa fa-truck fa-2x"}))
            (my-dom/div
              (css/grid-column)
              (dom/div nil
                (dom/div nil (dom/strong nil full-name))
                ;(dom/div nil (dom/span nil street1))
                (dom/div nil (dom/span nil (clojure.string/join ", " (filter some? [street1 postal locality region country]))))))
            (my-dom/div
              (->> (css/grid-column)
                   (css/add-class :shrink))
              (dom/a #js {:className "button hollow"}
                     (dom/i #js {:className "fa fa-pencil fa-fw"}))))))
      (dom/div #js {:className "callout"}
        (let [{:keys [last4 exp_year exp_month brand]} (:card payment)]
          (my-dom/div
            (->> (css/grid-row)
                 (css/align :bottom))
            (my-dom/div
              (->> (css/grid-column)
                   (css/grid-column-size {:small 3 :medium 2}))
              (dom/h4 nil "Payment")
              (dom/i #js {:className "fa fa-credit-card fa-2x"}))
            (my-dom/div
              (css/grid-column)
              (dom/div nil
                (dom/div nil (dom/span nil brand))
                (dom/div nil
                  (dom/span nil "**** **** **** "
                            (dom/strong nil last4)
                            " "
                            (dom/small nil (str exp_month "/" exp_year))))))
            (my-dom/div
              (->> (css/grid-column)
                   (css/add-class :shrink))
              (dom/a #js {:className "button hollow"}
                     (dom/i #js {:className "fa fa-pencil fa-fw"}))))))
      (dom/div #js {:className "callout"}
        (let [store (:store/_items (:store.item/_skus (first items)))]
          (debug "store: " store)
          (store-element store))
        (dom/div #js {:className "items"}
          (map (fn [sku]
                 (let [{:store.item/keys [price photos]
                        product-id       :db/id
                        item-name        :store.item/name} (get sku :store.item/_skus)]
                   (my-dom/div
                     (->> (css/grid-row)
                          ;(css/add-class :collapse)
                          (css/align :middle)
                          ;(css/add-class :callout)
                          (css/add-class :transparent)
                          (css/add-class :item))

                     (my-dom/div
                       (->> (css/grid-column)
                            (css/grid-column-size {:small 4 :medium 2}))
                       (photo/square
                         {:src (:photo/path (first photos))}))

                     ;(my-dom/div
                     ;  (->> (css/grid-column))
                     ;  (dom/a #js {:className "close-button"} (dom/small nil "x")))
                     (my-dom/div
                       (->> (css/grid-column))

                       (dom/div #js {:className ""}
                         (dom/a #js {
                                     ;:href      (routes/url :product {:product-id product-id})
                                     :className "name"}
                                (dom/span nil item-name)))
                       (dom/div #js {:className ""}
                         (dom/span nil (:store.item.sku/value sku))))

                     (my-dom/div
                       (->> (css/grid-column)
                            (css/align :right)
                            ;(css/add-class :shrink)
                            (css/grid-column-size {:small 3 :medium 2})
                            ;(css/grid-column-offset {:small 3 :large 0})
                            )
                       (dom/input #js {:type         "number"
                                       :defaultValue 1})
                       )
                     (my-dom/div
                       (->> (css/grid-column)
                            (css/text-align :right)
                            (css/add-class :shrink)
                            )
                       (dom/div #js {:className ""}
                         (dom/span #js {:className "price"}
                                   (utils/two-decimal-price price)))))))
               items))
        (dom/div #js {:className "receipt"}
          (my-dom/div
            (css/grid-row)
            (my-dom/div
              (->> (css/grid-column)
                   (css/text-align :right))
              (dom/span nil "Subtotal"))
            (my-dom/div
              (->> (css/grid-column)
                   (css/text-align :right))
              (dom/span nil (utils/two-decimal-price (compute-item-price items)))))
          (my-dom/div
            (css/grid-row)
            (my-dom/div
              (->> (css/grid-column)
                   (css/text-align :right))
              (dom/span nil "Shipping"))
            (my-dom/div
              (->> (css/grid-column)
                   (css/text-align :right))
              (dom/span nil (utils/two-decimal-price 5))))
          (my-dom/div
            (css/grid-row)
            (my-dom/div
              (->> (css/grid-column)
                   (css/text-align :right))
              (dom/span nil "Discount"))
            ;(my-dom/div
            ;  (->> (css/grid-column)
            ;       (css/text-align :right))
            ;  (dom/a nil "Add code"))
            (my-dom/div
              (->> (css/grid-column)
                   (css/text-align :right))
              (dom/span nil (utils/two-decimal-price 0))
              (dom/br nil)
              (dom/a nil "Add code")))
          (my-dom/div
            (css/add-class :total)
            (my-dom/div
              (css/grid-row)
              (my-dom/div
                (->> (css/grid-column)
                     (css/text-align :right))
                (dom/strong nil "Total"))
              (my-dom/div
                (->> (css/grid-column)
                     (css/text-align :right))
                (dom/strong nil (utils/two-decimal-price (+ 5 (compute-item-price items)))))))))
      (my-dom/div
        (css/grid-row)
        (my-dom/div
          (->> (css/grid-column)
               (css/text-align :right))
          (dom/div #js {:className "button"
                        :onClick   #(.place-order component)} "Place Order")))
      )))

(defn payment-element [component & [{:keys [sources]}]])

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
       (let [{:query/keys [current-route cart auth]} (om/props this)
             {:checkout/keys [shipping payment]} (om/get-state this)
             {:keys [card source]} payment
             {:keys [route-params]} current-route
             {:keys [store-id]} route-params]
         (let [items (filter #(= (c/parse-long store-id) (get-in % [:store.item/_skus :store/_items :db/id])) (:cart/items cart))]
           (debug "Order items: " items)
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
            ;(shipping-element this )
            (condp = progress
              1 (ship/->CheckoutShipping (om/computed {}
                                                      {:on-change #(om/update-state! this assoc :checkout/shipping %)}))
              2 (pay/->CheckoutPayment (om/computed {}
                                                    {:on-change #(om/update-state! this assoc :checkout/payment %)}))
              3 (confirm-element this))))))))

(def ->Checkout (om/factory Checkout))