(ns eponai.common.ui.store.order-edit-form
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.components.select :as sel]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.client.routes :as routes]
    #?(:cljs
       [eponai.web.utils :as utils])
    [eponai.client.parser.message :as msg]
    [taoensso.timbre :refer [debug]]
    [eponai.common.format.date :as date]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.utils :refer [two-decimal-price]]
    [eponai.web.ui.store.common :as store-common]
    [eponai.common.ui.elements.table :as table]
    [eponai.common.ui.elements.menu :as menu]))

(def form-elements
  {:input-currency "oef-input-currency"
   :input-price    "oef-input-price"})

(defn get-route-params [component]
  (get (om/get-computed component) :route-params))

(def modal-order-status-keys
  {:modal/mark-as-fulfilled? :order.status/fulfilled
   :modal/mark-as-returned?  :order.status/returned
   :modal/mark-as-canceled?  :order.status/canceled})

(def modal-title
  {:modal/mark-as-fulfilled? "Fulfill items"
   :modal/mark-as-returned?  "Return order"
   :modal/mark-as-canceled?  "Cancel order"})

(def modal-message
  {:modal/mark-as-fulfilled? "Do you want to fulfill items for this order?"
   :modal/mark-as-returned?  "Do you want to mark items as returned to customer? The payment will be returned in full to the customer."
   :modal/mark-as-canceled?  "Do you want to cancel this order? The payment will be returned in full to the customer."})

(def confirm-message
  {:modal/mark-as-fulfilled? "Yes, fullfill items!"
   :modal/mark-as-returned?  "Yes, return order!"
   :modal/mark-as-canceled?  "Yes, cancel order!"})

(def dropdowns
  {:dropdown/sulo-fee "dropdown-sulo-fee"})

(defn item-cell [opts & content]
  (table/td (css/add-class :sl-OrderItemlist-cell opts) content))

(defn order-status-modal [component]
  (let [{:keys [modal]} (om/get-state component)
        order-status (get modal-order-status-keys modal)
        on-close #(om/update-state! component dissoc :modal)]
    (when modal
      (common/modal
        {:on-close on-close}
        (dom/div nil
                 (dom/h4 (css/add-class :header) (get modal-title modal))
                 (callout/callout
                   nil
                   (dom/p nil (get modal-message modal))
                   (callout/header nil))
                 (dom/div (css/text-align :right)
                          (dom/a
                            (css/button-hollow {:onClick on-close})
                            (dom/span nil "Oops, no thanks"))
                          (dom/a
                            (css/button {:onClick #(do
                                                    (on-close)
                                                    (.update-order component {:order/status order-status}))})
                            (dom/span nil (get confirm-message modal)))))))))

(defn label-column [opts & content]
  (grid/column
    (->> (grid/column-size {:small 3})
         (css/text-align :right))
    content))

(defn edit-order [component]
  (let [{:keys [query/order]} (om/props component)
        {:keys [dropdown]} (om/get-state component)
        {:order/keys [id amount currency items store]} order

        skus (filter #(= :order.item.type/sku (:order.item/type %)) items)
        tax (some #(when (= :tax (:order.item/type %)) %) items)
        shipping (some #(when (= :shipping (:order.item/type %)) %) items)
        order-created (when-some [created (:order/created order)]
                        (date/date->string (* 1000 created) "MMM dd yyyy HH:mm"))
        grouped-orders (group-by :order.item/type items)
        subtotal (reduce + 0 (map :order.item/amount (:order.item.type/sku grouped-orders)))

        ;order (assoc order :order/status :order.status/returned)
        order-email (get-in order [:order/user :user/email])
        total-amount (reduce + 0 (map :order.item/amount items))
        order-status (:order/status order)]
    (dom/div
      nil
      (order-status-modal component)


      (dom/div
        (css/add-class :section-title)
        (dom/h2 nil
                (dom/span nil "Order ")
                (dom/small nil (str "#" (:db/id order)))))
      (callout/callout
        nil
        (grid/row
          (css/add-class :collapse)
          (grid/column
            (grid/column-size {:small 12 :large 6})
            (dom/div
              (css/add-class :order-action)
              (dom/div
                nil
                (if (or (= order-status :order.status/returned)
                        (= order-status :order.status/canceled))
                  (dom/i {:classes ["fa fa-rotate-left fa-fw fa-2x"]})
                  (dom/i {:classes ["fa fa-credit-card fa-fw fa-2x"]}))
                (cond (or (= order-status :order.status/paid)
                          (= order-status :order.status/fulfilled))
                      (dom/span nil "Payment accepted")

                      (= order-status :order.status/created)
                      (dom/span nil "Payment pending")

                      (or (= order-status :order.status/returned)
                          (= order-status :order.status/canceled))
                      (dom/span nil "Payment refunded")))))
          (grid/column
            (grid/column-size {:small 12 :large 6})
            (dom/div
              (css/add-class :order-action)
              (dom/div nil
                       (dom/i {:classes ["fa fa-truck fa-fw fa-2x"]})
                       (cond (= order-status :order.status/fulfilled)
                             (dom/span nil "Items fulfilled")
                             :else
                             (dom/span nil "Fulfill items")))
              (cond
                (= order-status :order.status/fulfilled)
                (dom/a
                  (css/button-hollow {:onClick #(om/update-state! component assoc :modal :modal/mark-as-returned?)})
                  "Return Items")
                (= order-status :order.status/returned)
                (dom/a nil "")
                :else
                (dom/a (cond->> (css/button {:onClick #(om/update-state! component assoc :modal :modal/mark-as-fulfilled?)})
                                (not= (:order/status order) :order.status/paid)
                                (css/add-class :disabled))
                       (dom/span nil "Fulfill Items")))))))

      ;(dom/div
      ;  (css/add-class :section-title)
      ;  (dom/h2 nil "Details"))

      (callout/callout
        nil
        (grid/row
          (css/add-class :collapse)
          (grid/column
            nil
            (grid/row
              nil
              (label-column nil (dom/label nil "ID: "))
              (grid/column
                nil
                (dom/p nil (:db/id order))))
            (grid/row
              nil
              (label-column nil (dom/label nil "Created: "))
              (grid/column
                nil
                (dom/p nil order-created)))
            (grid/row
              nil
              (label-column nil (dom/label nil "Email: "))
              (grid/column
                nil
                (dom/p nil (dom/a {:href (str "mailto:" order-email "?subject=" (:store.profile/name (:store/profile store)) " Order #" (:db/id order) "")} order-email))))
            (grid/row
              nil
              (label-column nil (dom/label nil "Status: "))
              (grid/column
                nil
                (dom/p nil (common/order-status-element order)))
              ))
          (grid/column
            (grid/column-size {:small 12 :medium 6})
            (let [shipping (:order/shipping order)
                  address (:shipping/address shipping)]

              (dom/div
                (css/add-class :shipping-address)
                (dom/p nil (dom/label nil "Ship to: "))
                (dom/p nil (:shipping/name shipping))
                (dom/div nil (dom/span nil (:shipping.address/street address)))
                (dom/div nil (dom/span nil (:shipping.address/street2 address)))
                (dom/div nil
                         (dom/span nil
                                   (str
                                     (:shipping.address/locality address)
                                     ", "
                                     (:shipping.address/postal address)
                                     " "
                                     (:shipping.address/region address)
                                     )))
                (dom/div nil (dom/span nil (:shipping.address/country address)))))))

        (callout/callout
          nil
          (table/table
            (->> (css/add-class :unstriped)
                 (css/add-class :stack))
            (table/thead
              nil
              (table/thead-row
                nil
                (table/th nil "ID")
                (table/th nil "Description")
                (table/th nil "Variation")
                (table/th nil "Price")))
            (table/tbody
              nil
              (map
                (fn [oi]
                  (let [sku (:order.item/parent oi)
                        product (:store.item/_skus sku)]
                    (table/thead-row
                      (->> (css/add-class :sl-OrderItemlist-row)
                           (css/add-class :sl-OrderItemlist-row--sku))
                      (item-cell
                        (css/add-class :sl-OrderItemlist-cell--id)
                        (dom/p nil
                               (dom/a {:href (routes/url :product {:product-id (:db/id product)})}
                                      (dom/span nil (:db/id product)))))
                      (item-cell
                        (css/add-class :sl-OrderItemlist-cell--description)
                        (dom/span nil (:store.item/name product)))
                      (item-cell
                        (css/add-class :sl-OrderItemlist-cell--variation)
                        (dom/span nil (:store.item.sku/variation sku)))
                      (item-cell
                        (css/add-class :sl-OrderItemlist-cell--price)
                        (dom/span nil (two-decimal-price (:store.item/price product)))))))
                (:order.item.type/sku grouped-orders))
              (let [shipping-item (first (:order.item.type/shipping grouped-orders))]
                (table/thead-row
                  (->> (css/add-class :sl-OrderItemlist-row)
                       (css/add-class :sl-OrderItemlist-row--shipping))
                  (item-cell (css/add-class :sl-OrderItemlist-cell--id) (dom/span nil "Shipping"))
                  (item-cell (css/add-class :sl-OrderItemlist-cell--description) (dom/span nil "Free shipping"))
                  (item-cell nil)
                  (item-cell (css/add-class :sl-OrderItemlist-cell--price) (dom/span nil (two-decimal-price (:order.item/amount shipping-item))))))
              (table/thead-row
                (->> (css/add-class :sl-OrderItemlist-row)
                     (css/add-class :sl-OrderItemlist-row--tax))
                (item-cell (css/add-class :sl-OrderItemlist-cell--id) (dom/span nil "Tax"))
                (item-cell (css/add-class :sl-OrderItemlist-cell--description) (dom/span nil "Taxes (included)"))
                (item-cell nil)
                (item-cell (css/add-class :sl-OrderItemlist-cell--price) (dom/span nil (two-decimal-price 0)))))
            (table/tfoot
              nil
              (table/thead-row
                (->> (css/add-class :sl-OrderItemlist-row)
                     (css/add-class :sl-OrderItemlist-row--fee))
                (item-cell (css/add-class :sl-OrderItemlist-cell--id)
                           (dom/div nil
                                    (dom/span nil "Fee")
                                    (dom/div
                                      (css/add-class :dropdown-trigger)
                                      (dom/a
                                        nil
                                        ;{:onClick #(.open-dropdown component :dropdown/sulo-fee)}
                                        (dom/i {:classes ["fa fa-question-circle-o fa-fw"]}))
                                      (dom/div
                                        (cond->> (css/add-class :dropdown-pane)
                                                 (= dropdown :dropdown/sulo-fee)
                                                 (css/add-class :is-open))
                                        (dom/p nil
                                               (dom/small nil "SULO Live service fee: ")
                                               (dom/small nil (two-decimal-price (* 0.2 subtotal))))
                                        (dom/p nil
                                               (dom/small nil "Stripe transaction fee: ")
                                               (dom/small nil (two-decimal-price (* 0.029 total-amount))))))))
                (item-cell (css/add-class :sl-OrderItemlist-cell--description)
                           ;(dom/span nil "SULO Live fee")
                           (dom/p nil (dom/span nil "Service fee"))
                           )
                (item-cell nil)
                (item-cell (css/add-class :sl-OrderItemlist-cell--price) (dom/span nil (two-decimal-price (+ (* 0.029 total-amount) (* 0.2 subtotal))))))
              (table/thead-row
                (->> (css/add-class :sl-OrderItemlist-row)
                     (css/add-class :sl-OrderItemlist-row--total))
                (item-cell (css/add-class :sl-OrderItemlist-cell--id) (dom/span nil "Total"))
                (item-cell nil)
                (item-cell nil)
                (item-cell (css/add-class :sl-OrderItemlist-cell--price) (dom/span nil (two-decimal-price (:order/amount order)))))))))

      (grid/row
        (css/align :bottom)
        ;(grid/column
        ;  nil
        ;  ;(dom/h3 nil (dom/span nil "Edit product - ") (dom/small nil item-name))
        ;  (dom/h3 nil (dom/span nil "Order - ") (dom/small nil (str "#" (:db/id order)))))
        (grid/column
          (css/text-align :right)
          (when (or (= order-status :order.status/created)
                    (= order-status :order.status/paid))
            (dom/a
              (->> (css/button-hollow {:onClick #(om/update-state! component assoc :modal :modal/mark-as-canceled?)})
                   (css/add-class :alert)) "Cancel Order")))))))

(defn create-order [component]
  (let [{:keys [products]} (om/get-computed component)]
    (dom/div
      nil
      (grid/row-column
        nil
        (dom/h2 nil "New Order"))
      (grid/row-column
        nil
        (callout/callout
          nil
          (dom/h4 nil (dom/span nil "Details"))
          (grid/row-column
            nil
            (dom/label nil "Product")
            (sel/->SelectOne (om/computed {:value   {:label "Hej" :value "test"}
                                           :options (mapv (fn [p]
                                                            {:label (:store.item/name p)
                                                             :value (:db/id p)})
                                                          products)}
                                          {:on-change (fn [p]
                                                        (debug "P: " p)
                                                        (om/update-state! component update :items conj (:value p)))}))
            ;(my-dom/select {:id           (get form-elements :input-product)}
            ;               (my-dom/option {:value "test"} "Hej"))
            ))))))

(defn order-not-found [component]
  (let [{:query/keys [current-route]} (om/props component)
        {:keys [order-id store-id]} (:route-params current-route)]
    (dom/div nil
             (dom/div
               (css/add-class :section-title)
               (dom/h2 nil
                       (dom/span nil "Order not found")))
             (callout/callout
               (->> (css/text-align :center)
                    (css/add-class :not-found))
               (dom/p nil (dom/i {:classes ["fa fa-times-circle fa-2x"]}))
               (dom/p nil
                      (dom/strong nil (str "Order #" order-id " was not found in "))
                      (dom/strong nil (dom/a {:href (routes/url :store-dashboard/order-list {:store-id store-id})}
                                             (dom/span nil "your orders"))))))))

(defui OrderEditForm
  static om/IQuery
  (query [_]
    [:query/messages
     {:query/order [:db/id
                    {:order/items [:order.item/type
                                   :order.item/amount
                                   {:order.item/parent [:store.item.sku/variation {:store.item/_skus [:db/id :store.item/name :store.item/price]}]}]}
                    :order/amount
                    :order/status
                    :order/subtotal
                    {:order/charge [:charge/id
                                    :charge/amount]}
                    {:order/user [:user/email]}
                    {:order/shipping [:shipping/name
                                      {:shipping/address [:shipping.address/country
                                                          :shipping.address/street
                                                          :shipping.address/street2
                                                          :shipping.address/postal
                                                          :shipping.address/locality
                                                          :shipping.address/region]}]}
                    {:order/store [{:store/profile [:store.profile/name {:store.profile/photo [:photo/path]}]}]}]}
     :query/current-route])

  static store-common/IDashboardNavbarContent
  (render-subnav [_ current-route]
    (menu/breadcrumbs
      nil
      (menu/item nil (dom/a {:href (routes/url :store-dashboard/order-list (:route-params current-route))}
                            "Orders"))
      (menu/item nil (dom/span nil "Order details"))))

  Object
  #?(:cljs
     (open-dropdown
       [this dd-key]
       (let [{:keys [on-dropdown-close]} (om/get-state this)]
         (om/update-state! this assoc :dropdown dd-key)
         (.addEventListener js/document "click" on-dropdown-close))))

  #?(:cljs
     (close-dropdown
       [this click-event]
       (let [{:keys [dropdown on-dropdown-close]} (om/get-state this)
             open-dropdown-id (get dropdowns dropdown)]
         (when-not (= (.-id (.-target click-event)) open-dropdown-id)
           (om/update-state! this dissoc :dropdown)
           (.removeEventListener js/document "click" on-dropdown-close)))))

  (update-order [this params]
    (let [{:keys [order-id store-id]} (get-route-params this)]
      (msg/om-transact! this `[(store/update-order ~{:params   params
                                                     :order-id order-id
                                                     :store-id store-id})
                               :query/order])))
  (initLocalState [this]
    {:items             #{}
     :on-dropdown-close (fn [dropdown-key] (.close-dropdown this dropdown-key))})
  (componentDidMount [this]
    (om/update-state! this assoc :did-mount? true))

  (render [this]
    (let [{:query/keys [order current-route]} (om/props this)
          {:keys [order-id store-id]} (:route-params current-route)
          {:keys [products]} (om/get-computed this)
          {:keys [input-items did-mount?]} (om/get-state this)
          is-loading? false
          filtered (filter #(contains? (set input-items) (:db/id %)) products)]

      (debug "CURRENT ORDER: " order)
      (dom/div
        {:id "sulo-edit-order"}
        ;(when-not did-mount?
        ;  (common/loading-spinner nil))
        (dom/h1 (css/show-for-sr) "Edit order")
        ;(grid/row-column
        ;  nil
        ;  (menu/breadcrumbs
        ;    nil
        ;    (menu/item nil (dom/a {:href (routes/url :store-dashboard/order-list {:store-id store-id})}
        ;                          "Orders"))
        ;    (menu/item nil (dom/span nil "Order details"))))
        (if (common/is-order-not-found? this)
          (order-not-found this)
          (if (common/is-new-order? this)
            (create-order this)
            (edit-order this)))

        ;(when-not order
        ;  (my-dom/div
        ;    (->> (css/grid-row)
        ;         (css/grid-column))
        ;    (my-dom/div nil
        ;                (my-dom/a
        ;                  (->> (css/button)
        ;                       css/button-hollow)
        ;                  (my-dom/span nil "Cancel"))
        ;                (my-dom/a
        ;                  (->> {:onClick #(when-not is-loading? (.create-order this))}
        ;                       (css/button))
        ;                  (if is-loading?
        ;                    (my-dom/i {:className "fa fa-spinner fa-spin"})
        ;                    (my-dom/span nil "Save"))))))
        ))))

(def ->OrderEditForm (om/factory OrderEditForm))

