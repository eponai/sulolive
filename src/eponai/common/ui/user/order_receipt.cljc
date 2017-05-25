(ns eponai.common.ui.user.order-receipt
  (:require
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.utils :refer [two-decimal-price]]
    [eponai.common.ui.common :as common]
    [eponai.client.routes :as routes]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.table :as table]
    [eponai.common.format.date :as date]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.web.ui.photo :as photo]))

(defn store-element [s]
  (let [{:store/keys  [photo]
         {store-name :store.profile/name} :store.profile} s]
    (dom/div
      nil
      (photo/store-photo s {:transformation :transformation/thumbnail-tiny})
      (dom/div
        (css/text-align :center)
        (dom/p nil (dom/strong
                     (css/add-class :store-name) store-name))))))

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
                     (photo/store-photo store {:transformation :transformation/thumbnail-tiny}))
                   (dom/p nil
                          (dom/span nil "Purchased from ")
                          (dom/a {:href (routes/url :store {:store-id (:db/id store)})} (:store.profile/name (:store/profile store)))))
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
                          (photo/product-preview item))
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
              (dom/h2 nil (two-decimal-price (:order/amount order))))))))))

(defui Order
  static om/IQuery
  (query [_]
    [:query/current-route
     {:query/order [:db/id
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
                    {:order/store [{:store/profile [{:store.profile/photo [:photo/path]}
                                                    :store.profile/name]}]}]}])
  Object
  (render [this]
    (let [{:query/keys [current-route order]} (om/props this)
          {:keys [route route-params]} current-route]
      (debug "Order receipt: " order)
      (dom/div
        {:id "sulo-order-receipt"}

        (grid/row-column
          nil
          (menu/breadcrumbs
            nil
            (menu/item nil (dom/a {:href (routes/url :user/order-list route-params)} (dom/span nil "My orders")))
            (menu/item nil (dom/span nil "Order"))))
        (if (common/is-order-not-found? this)
          (common/order-not-found this (routes/url :user/order-list route-params))
          (grid/row-column
            nil
            (dom/h1 nil (dom/span nil (str "Order #" (:order-id route-params))))
            (order-element this order)))))))

(def ->Order (om/factory Order))
