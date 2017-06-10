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
    [eponai.common.ui.utils :as ui-utils]
    [eponai.web.ui.store.common :as store-common]
    [eponai.common.ui.elements.table :as table]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.web.ui.button :as button]
    [eponai.web.ui.photo :as photo]))

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
  (let [{:query/keys [order current-route]} (om/props component)
        {:keys [dropdown]} (om/get-state component)
        {:order/keys [id amount currency items store]} order
        {store-name :store.profile/name} (:store/profile store)

        skus (filter #(= :order.item.type/sku (:order.item/type %)) items)
        tax (some #(when (= :tax (:order.item/type %)) %) items)
        shipping (some #(when (= :shipping (:order.item/type %)) %) items)
        order-created (when-some [created (:order/created-at order)]
                        (date/date->string created "MMMM dd YYYY"))
        grouped-orders (group-by :order.item/type items)
        subtotal (reduce + 0 (map :order.item/amount (:order.item.type/sku grouped-orders)))

        ;order (assoc order :order/status :order.status/returned)
        order-email (get-in order [:order/user :user/email])
        total-amount (reduce + 0 (map :order.item/amount items))
        order-status (:order/status order)
        delivery (some #(when (= (:order.item/type %) :order.item.type/shipping) %) (:order/items order))
        sulo-fee (some #(when (= (:order.item/type %) :order.item.type/sulo-fee) %) (:order/items order))]
    (dom/div
      nil
      (order-status-modal component)


      (grid/row-column
        (css/add-class :go-back)
        (dom/a
          {:href (routes/url :store-dashboard/order-list (:route-params current-route))}
          (dom/span nil "Back to order list")))
      (dom/div
        (css/add-class :section-title)
        (dom/h1 nil
                (dom/span nil "Order ")
                (dom/small nil (str "#" (:db/id order)))))
      (callout/callout
        nil
        (grid/row-column
          nil
          (dom/h2 nil
                  (common/order-status-element order)
                  (dom/span nil (:shipping/name (:order/shipping order))))
          ;(dom/p nil (dom/span nil order-email))
          (dom/a
            (->> (css/button-hollow {:href (str "mailto:" order-email "?subject=SULO Live order #" (:db/id order) " from " store-name)})
                 (css/add-class :small))
            (dom/i {:classes ["fa fa-envelope-o"]})
            (dom/span nil "Contact shopper"))
          (dom/label nil "Created")
          (dom/p nil order-created))
        (let [shipping (:order/shipping order)
              address (:shipping/address shipping)]
          (grid/row
            (->> (grid/columns-in-row {:small 1 :medium 2})
                 (css/align :bottom))
            (grid/column
              nil
              (dom/div
                (css/add-class :shipping-address)
                (dom/label nil "Ship to")
                (dom/p nil (dom/strong nil (:shipping/name shipping))
                       (dom/br nil)
                       (dom/span nil (:shipping.address/street address))
                       (dom/br nil)
                       (when (not-empty (:shipping.address/street2 address))
                         [
                          (dom/span nil (:shipping.address/street2 address))
                          (dom/br nil)])
                       (dom/span nil
                                 (str
                                   (:shipping.address/locality address)
                                   ", "
                                   (:shipping.address/postal address)
                                   " "
                                   (:shipping.address/region address)
                                   ))
                       (dom/br nil)
                       (dom/span nil (:shipping.address/country address))))
              (dom/label nil "Shipping option")
              (dom/p nil (dom/span nil (:order.item/title delivery))
                     (dom/br nil)
                     (dom/small
                       nil
                       (dom/strong nil "Note to customer: ")
                       (dom/span nil (:order.item/description delivery)))))
            (grid/column
              nil
              (dom/div
                (css/add-class :action-buttons)
                (cond
                  (= order-status :order.status/fulfilled)
                  (dom/a
                    (css/button-hollow {:onClick #(om/update-state! component assoc :modal :modal/mark-as-returned?)})
                    "Mark as returned")
                  (= order-status :order.status/returned)
                  (dom/a nil "")
                  :else
                  (dom/a (cond->> (css/button {:onClick #(om/update-state! component assoc :modal :modal/mark-as-fulfilled?)})
                                  (not= (:order/status order) :order.status/paid)
                                  (css/add-class :disabled))
                         (dom/span nil "Mark as shipped")))
                (when (or (= order-status :order.status/created)
                          (= order-status :order.status/paid))
                  (dom/div
                    nil
                    (dom/a
                      (->> (css/button-hollow {:onClick #(om/update-state! component assoc :modal :modal/mark-as-canceled?)})
                           (css/add-classes [:secondary])) "Cancel order"))))
              )))
        )

      (callout/callout
        nil
        (grid/row-column
          nil
          (dom/h2 nil "Products")
          (menu/vertical
            (css/add-class :section-list)
            (map (fn [oi]
                   (let [sku (:order.item/parent oi)
                         product (:store.item/_skus sku)]
                     (menu/item
                       (css/add-classes [:section-list-item :order-item])
                       (grid/row
                         nil
                         (grid/column
                           (->> (grid/column-size {:small 4 :medium 6})
                                (css/add-class :order-item-info))
                           (photo/product-photo {:photo-id       (get-in oi [:order.item/photo :photo/id])
                                          :transformation :transformation/thumbnail})
                           (dom/p nil (dom/span nil (:db/id product))
                                  (dom/br nil)
                                  (dom/small nil (:order.item/title oi))))
                         ;(grid/column
                         ;  (css/add-class :order-item-info)

                         ;  (dom/p nil (dom/span nil (:order.item/title oi))))
                         (grid/column nil (dom/p nil (dom/span nil (:order.item/description oi))))
                         (grid/column
                           (->> (css/text-align :right)
                                (css/add-class :order-item-price))
                           (dom/p nil
                                  (ui-utils/two-decimal-price (:order.item/amount oi))))))))
                 (filter #(= (:order.item/type %) :order.item.type/sku) (:order/items order)))

            (menu/item
              (css/add-class :secondary-items)
              (grid/row
                nil
                (grid/column (grid/column-size {:small 4 :medium 6}))
                (grid/column
                  (grid/column-size {:small 4 :medium 3})
                  (dom/p nil "Subtotal"))
                (grid/column
                  (->> (grid/column-size {:small 4 :medium 3})
                       (css/text-align :right))
                  (dom/p nil (ui-utils/two-decimal-price (apply + (map :order.item/amount (filter #(= (:order.item/type %) :order.item.type/sku) items)))))))
              (grid/row
                nil
                (grid/column (grid/column-size {:small 4 :medium 6})
                             (dom/p nil (dom/span nil (:order.item/title delivery))))
                (grid/column
                  (grid/column-size {:small 4 :medium 3})
                  (dom/p nil "Shipping"))
                (grid/column
                  (->> (grid/column-size {:small 4 :medium 3})
                       (css/text-align :right))
                  (dom/p nil (ui-utils/two-decimal-price (:order.item/amount delivery)))))
              (grid/row
                nil
                (grid/column (grid/column-size {:small 4 :medium 6}))
                (grid/column
                  (grid/column-size {:small 4 :medium 3})
                  (dom/p nil "Tax"))
                (grid/column
                  (->> (grid/column-size {:small 4 :medium 3})
                       (css/text-align :right))
                  (dom/p nil (ui-utils/two-decimal-price 0))))
              (grid/row
                nil
                (grid/column (grid/column-size {:small 4 :medium 6}))
                (grid/column
                  (grid/column-size {:small 4 :medium 3})
                  (dom/p nil (:order.item/description sulo-fee)))
                (grid/column
                  (->> (grid/column-size {:small 4 :medium 3})
                       (css/text-align :right))
                  (dom/p nil (str "-" (ui-utils/two-decimal-price (:order.item/amount sulo-fee))))))))
          (grid/row
            (css/add-class :total-price)
            (grid/column (grid/column-size {:small 4 :medium 6}))
            (grid/column (grid/column-size {:small 4 :medium 3})
                         (dom/strong nil "Total"))
            (grid/column
              (->> (grid/column-size {:small 4 :medium 3})
                   (css/text-align :right))
              (dom/strong nil (ui-utils/two-decimal-price (- (:order/amount order) (:order.item/amount sulo-fee))))))))

      (grid/row-column
        (css/add-class :go-back)
        (dom/a
          {:href (routes/url :store-dashboard/order-list (:route-params current-route))}
          (dom/span nil "Back to order list")))

      ;(grid/row
      ;  (css/align :bottom)
      ;  ;(grid/column
      ;  ;  nil
      ;  ;  ;(dom/h3 nil (dom/span nil "Edit product - ") (dom/small nil item-name))
      ;  ;  (dom/h3 nil (dom/span nil "Order - ") (dom/small nil (str "#" (:db/id order)))))
      ;  (grid/column
      ;    nil
      ;    (when (or (= order-status :order.status/created)
      ;              (= order-status :order.status/paid))
      ;      (dom/a
      ;        (->> (css/button-hollow {:onClick #(om/update-state! component assoc :modal :modal/mark-as-canceled?)})
      ;             (css/add-class :alert)) "Cancel Order"))))
      )))

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
                                   :order.item/description
                                   :order.item/title
                                   {:order.item/parent [:store.item.sku/variation {:store.item/_skus [:db/id :store.item/name :store.item/price]}]}]}
                    :order/amount
                    :order/status
                    :order/created-at
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

