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
        {:store/keys [profile]} store
        {:keys [route-params]} current-route]
    ;(let [skus (filter #(= (:order.item/type %) :order.item.type/sku) (:order/items order))
    ;      not-skus (remove #(= (:order.item/type %) :order.item.type/sku) (:order/items order))])
    (dom/div
      (css/add-class :sulo-order-element)
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
                        (dom/a {:href (routes/url :store {:store-id (:db/id store)})} (:store.profile/name profile))))
        (dom/p nil (date/date->string (date/current-millis))))
      (callout/callout
        nil


        (grid/row
          (css/align :top)
          (grid/column
            (grid/column-size {:small 12 :large 8})
            (table/table
              nil
              (table/tbody
                nil
                (map
                  (fn [oi]
                    (debug "Order item: " oi)
                    (let [sku (:order.item/parent oi)
                          item (:store.item/_skus sku)
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
                               (css/add-class :sl-OrderItemlist-cell--description))
                          (dom/div nil (dom/span nil (:store.item/name item)))
                          (dom/div nil (dom/small nil (:store.item.sku/variation sku))))
                        (table/td
                          (->> (css/add-class :sl-OrderItemlist-cell)
                               (css/add-class :sl-OrderItemlist-cell--price)
                               (css/text-align :right))
                          (dom/div nil (dom/span nil (two-decimal-price (:store.item/price item))))))))
                  (:order.item.type/sku grouped-order-items))
                )))
          (grid/column
            (css/text-align :right)

            (dom/h3 nil (str "Amount: " (two-decimal-price (:order/amount order))))
            (dom/a
              (css/button-hollow {:href (routes/url :user/order {:order-id (:db/id order)
                                                                 :user-id  (get-in current-route [:route-params :user-id])})})
              (dom/span nil "View Order"))))))))

(defui OrderList
  static om/IQuery
  (query [_]
    [:query/current-route
     {:query/orders [:db/id
                     :order/uuid
                     :order/status
                     :order/amount
                     {:order/items [:order.item/type
                                    {:order.item/parent [:store.item.sku/variation
                                                         {:store.item/_skus [:store.item/name
                                                                             :store.item/price
                                                                             {:store.item/photos [{:store.item.photo/photo [:photo/path]}
                                                                                                  :store.item.photo/index]}]}]}]}
                     {:order/shipping [:shipping/name
                                       :shipping/address]}
                     :order/user
                     {:order/store [{:store/profile [{:store.profile/photo [:photo/path]}
                                                     :store.profile/name]}]}]}])
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