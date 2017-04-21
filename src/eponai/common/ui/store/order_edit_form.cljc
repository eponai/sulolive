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
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.format.date :as date]
    [eponai.common.ui.elements.grid :as grid]))

(def form-elements
  {:input-currency "oef-input-currency"
   :input-price    "oef-input-price"})

(defn get-route-params [component]
  (get (om/get-computed component) :route-params))

(def modal-order-status-keys
  {:modal/mark-as-fulfilled? :order.status/fulfilled
   :modal/mark-as-returned?  :order.status/returned
   :modal/mark-as-canceled?  :order.status/canceled})

(def modal-message
  {:modal/mark-as-fulfilled? "Do you want to fulfill items for this order?"
   :modal/mark-as-returned?  "Do you want to mark items as returned to customer?"
   :modal/mark-as-canceled?  "Do you want to cancel this order?"})

(defn order-status-modal [component]
  (let [{:keys [modal]} (om/get-state component)
        order-status (get modal-order-status-keys modal)
        on-close #(om/update-state! component dissoc :modal)]
    (when modal
      (common/modal
        {:on-close on-close}
        (dom/div nil
                 (dom/h4 nil (get modal-message modal))
                 (dom/div nil

                          (dom/a
                            (css/button {:onClick #(do
                                                          (on-close)
                                                          (.update-order component {:order/status order-status}))})
                            (dom/span nil "Yes, Shoot!"))
                          (dom/a
                            (css/button-hollow {:onClick on-close})
                            (dom/span nil "Oops, no thanks."))))))))

(defn label-column [opts & content]
  (grid/column
    (->> (grid/column-size {:small 4 :medium 3 :large 2})
         (css/text-align :right))
    content))

(defn edit-order [component]
  (let [{:keys [query/order]} (om/props component)
        {:order/keys [id amount currency items]} order

        skus (filter #(= :sku (:order.item/type %)) items)
        tax (some #(when (= :tax (:order.item/type %)) %) items)
        shipping (some #(when (= :shipping (:order.item/type %)) %) items)
        order-created (when-some [created (:order/created order)]
                        (date/date->string (* 1000 created) "MMM dd yyyy HH:mm"))

        ;order (assoc order :order/status :order.status/returned)
        order-email (get-in order [:order/user :user/email])
        order-status (:order/status order)]
    (dom/div
      nil
      (order-status-modal component)

      (grid/row
        (css/align :bottom)
        (grid/column
          nil
          ;(dom/h3 nil (dom/span nil "Edit product - ") (dom/small nil item-name))
          (dom/h3 nil (dom/span nil "Order - ") (dom/small nil (str "#" (:db/id order)))))
        (grid/column
          (->> (css/add-class :shrink)
               (css/text-align :right))
          (when (or (= order-status :order.status/created)
                    (= order-status :order.status/paid))
            (dom/a
              (->> (css/button-hollow {:onClick #(om/update-state! component assoc :modal :modal/mark-as-canceled?)})
                   (css/add-class :alert)) "Cancel Order"))))
      (grid/row-column
        nil
        (callout/callout
          nil
          (callout/header nil "Details")

          (callout/callout
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
                (dom/p nil (dom/a {:href (str "mailto:" order-email "?subject=" (:store/name (:order/store order)) " Order #" (:db/id order) "")} order-email))))
            (grid/row
              nil
              (label-column nil (dom/label nil "Status: "))
              (grid/column
                nil
                (dom/p nil (common/order-status-element order)))
              ))
          (callout/callout
            nil
            (grid/row
              nil
              (grid/column
                (grid/column-size {:small 12 :large 6})
                (dom/div
                  (css/add-class :order-action)
                  (dom/div
                    nil
                    (dom/i {:classes ["fa fa-credit-card fa-fw fa-2x"]})
                    (cond (or (= order-status :order.status/paid)
                              (= order-status :order.status/fulfilled))
                          (dom/span nil "Payment Accepted")

                          (= order-status :order.status/created)
                          (dom/span nil "Payment Pending")

                          (= order-status :order.status/returned)
                          (dom/span nil "Payment Returned")))))
              (grid/column
                (grid/column-size {:small 12 :large 6})
                (dom/div
                  (css/add-class :order-action)
                  (dom/div nil
                           (dom/i {:classes ["fa fa-truck fa-fw fa-2x"]})
                           (cond (= order-status :order.status/fulfilled)
                                 (dom/span nil "Items Fulfilled")
                                 :else
                                 (dom/span nil "Fulfill Items")))
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

          (callout/header nil "Items")
          (callout/callout
            nil
            (menu/vertical
              nil
              (map (fn [i]
                     (menu/item
                       (css/add-class :order-item)
                       (dom/span nil (str i))))))))))))

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
    (grid/row-column
      nil
      (dom/h3 nil "Order not found")
      (callout/callout
        (->> (css/text-align :center)
             (css/add-class :not-found))
        (dom/p nil (dom/i {:classes ["fa fa-times-circle fa-2x"]}))
        (dom/p nil
               (dom/strong nil (str "Order #" order-id " was not found in "))
               (dom/a {:href (routes/url :store-dashboard/order-list {:store-id store-id})}
                      (dom/strong nil "your orders")))))))

(defn is-new-order? [component]
  (let [{:query/keys [current-route]} (om/props component)]
    (nil? (get-in current-route [:route-params :order-id]))))

(defn is-order-not-found? [component]
  (let [{:query/keys [current-route order]} (om/props component)]
    (and (some? (get-in current-route [:route-params :order-id]))
         (nil? order))))

(defui OrderEditForm
  static om/IQuery
  (query [_]
    [:query/messages
     {:query/order [:db/id
                    {:order/items [:order.item/type
                                   {:order.item/parent [:store.item.sku/variation]}]}
                    :order/status
                    {:order/user [:user/email]}
                    {:order/store [:store/name {:store/photo [:photo/path]}]}]}
     :query/current-route])
  Object
  (update-order [this params]
    (let [{:keys [order-id store-id]} (get-route-params this)]
      (msg/om-transact! this `[(store/update-order ~{:params   params
                                                     :order-id order-id
                                                     :store-id store-id})
                               :query/orders])))
  (initLocalState [_]
    {:items #{}})
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
        (when-not did-mount?
          (common/loading-spinner nil))
        (if (is-order-not-found? this)
          (order-not-found this)
          (if (is-new-order? this)
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

