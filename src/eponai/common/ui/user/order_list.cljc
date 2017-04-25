(ns eponai.common.ui.user.order-list
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.photo :as photo]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]
    [eponai.client.routes :as routes]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.table :as table]
    [eponai.common.ui.utils :refer [two-decimal-price]]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.format.date :as date]))

(defn order-element [component order]
  (let [{:query/keys [current-route]} (om/props component)
        grouped-order-items (group-by :order.item/type (:order/items order))
        {:order/keys [store]} order
        {:keys [route-params]} current-route]
    ;(let [skus (filter #(= (:order.item/type %) :order.item.type/sku) (:order/items order))
    ;      not-skus (remove #(= (:order.item/type %) :order.item.type/sku) (:order/items order))])
    (dom/div
      (css/add-class :sulo-order-element)
      (callout/callout
        nil
        (dom/div
          (css/add-class :header)
          (dom/div nil
                   (dom/a
                     {:href (routes/url :store {:store-id (:db/id store)})}
                     (photo/store-photo store))
                   (dom/p nil
                          (dom/span nil "Order ")
                          (dom/a {:href (routes/url :user/order (assoc route-params :order-id (:db/id order)))} (dom/strong nil (str "#" (:db/id order))))
                          (dom/span nil " from ")
                          (dom/a {:href (routes/url :store {:store-id (:db/id store)})} (:store/name store))))
          (dom/p nil (date/date->string (date/current-millis))))

        (grid/row
          (css/align :top)
          (grid/column
            nil
            (table/table
              nil
              (table/tbody
                nil
                (map
                  (fn [oi]
                    (debug "Order item: " oi)
                    (let [item (get-in oi [:order.item/parent :store.item/_skus])
                          photos (:store.item/photos item)
                          sorted-photos (sort-by :store.item.photo/index photos)]
                      (table/tbody-row
                        nil
                        (table/td
                          (->> (css/add-class :sl-OrderItemlist-cell)
                               (css/add-class :sl-OrderItemlist-cell--photo))
                          (photo/product-photo (:store.item.photo/photo (first sorted-photos))))
                        (table/td
                          (->> (css/add-class :sl-OrderItemlist-cell)
                               (css/add-class :sl-OrderItemlist-cell--photo)) (:store.item/name item)))))
                  (:order.item.type/sku grouped-order-items))
                )))
          (grid/column
            (->> (css/text-align :right)
                 (css/add-class :shrink))

            (grid/row-column
              nil
              (dom/h2 nil (two-decimal-price (:order/amount order))))
            (grid/row-column
              nil
              (dom/a
                (css/button-hollow {:href (routes/url :user/order {:order-id (:db/id order)
                                                                   :user-id  (get-in current-route [:route-params :user-id])})})
                (dom/span nil "View Order")))))))))

(defui OrderList
  static om/IQuery
  (query [_]
    [:query/current-route
     {:query/orders [:db/id
                     :order/uuid
                     :order/status
                     :order/amount
                     {:order/items [:order.item/type
                                    {:order.item/parent [{:store.item/_skus [:store.item/name
                                                                             {:store.item/photos [{:store.item.photo/photo [:photo/path]}
                                                                                                  :store.item.photo/index]}]}]}]}
                     {:order/shipping [:shipping/name
                                       :shipping/address]}
                     :order/user
                     {:order/store [{:store/photo [:photo/path]}
                                    :store/name]}]}])
  Object
  (render [this]
    (let [{:query/keys [orders current-route]} (om/props this)]
      (debug "Got orders: " orders)
      (dom/div
        {:id "sulo-user-order-list"}
        (grid/row-column
          nil
          (common/wip-label this)
          (dom/h1 nil "My Orders")
          (map #(order-element this %) orders)
          )))))

(def ->OrderList (om/factory OrderList))