(ns eponai.common.ui.user.order-list
  (:require
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]
    [eponai.client.routes :as routes]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.table :as table]
    [eponai.common.ui.utils :as ui-utils]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.format.date :as date]
    [eponai.web.ui.photo :as photo]
    [eponai.common.ui.elements.menu :as menu]
    [clojure.string :as string]
    [eponai.web.ui.button :as button]))

(defn order-element [component order]
  (let [{:query/keys [current-route]} (om/props component)
        grouped-order-items (group-by :order.item/type (:order/items order))
        {:order/keys [store]} order
        {:store/keys [profile]} store
        {:keys [route-params]} current-route]
    ;(let [skus (filter #(= (:order.item/type %) :order.item.type/sku) (:order/items order))
    ;      not-skus (remove #(= (:order.item/type %) :order.item.type/sku) (:order/items order))])
    (callout/callout
      nil
      )
    ;(dom/div
    ;  (css/add-class :sulo-order-element)
    ;  (callout/callout
    ;    nil
    ;    (dom/div
    ;      (css/add-class :header)
    ;      (dom/div nil
    ;               (dom/a
    ;                 {:href (routes/url :store {:store-id (:db/id store)})}
    ;                 (photo/store-photo store {:transformation :transformation/thumbnail-tiny}))
    ;               (dom/p nil
    ;                      (dom/span nil "Order ")
    ;                      (dom/a {:href (routes/url :user/order (assoc route-params :order-id (:db/id order)))} (dom/strong nil (str "#" (:db/id order))))
    ;                      (dom/span nil " from ")
    ;                      (dom/a {:href (routes/url :store {:store-id (:db/id store)})} (:store.profile/name profile))))
    ;      (dom/p nil (date/date->string (date/current-millis))))
    ;
    ;    (grid/row
    ;      (css/align :top)
    ;      (grid/column
    ;        (grid/column-size {:small 12 :large 8})
    ;        (table/table
    ;          nil
    ;          (table/tbody
    ;            nil
    ;            (map
    ;              (fn [oi]
    ;                (debug "Order item: " oi)
    ;                (let [sku (:order.item/parent oi)
    ;                      item (:store.item/_skus sku)
    ;                      photos (:store.item/photos item)
    ;                      sorted-photos (sort-by :store.item.photo/index photos)]
    ;                  (table/tbody-row
    ;                    nil
    ;                    (table/td
    ;                      (->> (css/add-class :sl-OrderItemlist-cell)
    ;                           (css/add-class :sl-OrderItemlist-cell--photo))
    ;                      (photo/product-preview item))
    ;                    (table/td
    ;                      (->> (css/add-class :sl-OrderItemlist-cell)
    ;                           (css/add-class :sl-OrderItemlist-cell--description))
    ;                      (dom/div nil (dom/span nil (:store.item/name item)))
    ;                      (dom/div nil (dom/small nil (:store.item.sku/variation sku))))
    ;                    (table/td
    ;                      (->> (css/add-class :sl-OrderItemlist-cell)
    ;                           (css/add-class :sl-OrderItemlist-cell--price)
    ;                           (css/text-align :right))
    ;                      (dom/div nil (dom/span nil (two-decimal-price (:store.item/price item))))))))
    ;              (:order.item.type/sku grouped-order-items))
    ;            )))
    ;      (grid/column
    ;        (css/text-align :right)
    ;
    ;        (dom/h3 nil (str "Amount: " (two-decimal-price (:order/amount order))))
    ;        (dom/a
    ;          (css/button-hollow {:href (routes/url :user/order {:order-id (:db/id order)
    ;                                                             :user-id  (get-in current-route [:route-params :user-id])})})
    ;          (dom/span nil "View Order"))))))
    ))

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
                                                                             {:store.item/photos [{:store.item.photo/photo [:photo/id]}
                                                                                                  :store.item.photo/index]}]}]}]}
                     {:order/shipping [:shipping/name
                                       ;{:shipping/address  [:shipping.address/street]}
                                       {:shipping/address [:shipping.address/street
                                                           :shipping.address/postal
                                                           :shipping.address/locality
                                                           :shipping.address/region
                                                           :shipping.address/country]}
                                       ]}
                     :order/created-at
                     :order/user
                     {:order/store [{:store/profile [{:store.profile/photo [:photo/id]}
                                                     {:store.profile/cover [:photo/id]}
                                                     :store.profile/name]}]}]}])

  Object
  (render [this]
    (let [{:query/keys [orders current-route]} (om/props this)
          orders-by-month (group-by #(date/month->long (:order/created-at % 0)) orders)]
      (debug "Got orders: " orders)
      (dom/div
        {:id "sulo-user-order-list"}
        (grid/row-column
          nil

          (dom/h1 nil "Purchases")
          (map (fn [[timestamp os]]
                 [
                  (dom/h2 nil (date/date->string timestamp "MMMM YYYY"))
                  (map
                    (fn [o]
                      (let [{:order/keys [store]} o
                            {store-name :store.profile/name} (:store/profile store)]
                        (dom/div
                          (css/add-class :sl-order-card)
                          ;(photo/store-cover store nil)
                          (dom/div
                            (css/add-classes [:section-title :sl-order-card-title])

                            (dom/a
                              (css/add-class :sl-order-card-title--store {:href (routes/url :store {:store-id (:db/id store)})})
                              (photo/store-photo store {:transformation :transformation/thumbnail})
                              (dom/p nil
                                     (dom/span nil (str store-name))))
                            (dom/div
                              (css/text-align :right)
                              (dom/p nil (dom/small nil (date/date->string (:order/created-at o) "MMMM dd, YYYY")))
                              (button/user-setting-default {:href (routes/url :user/order {:order-id (:db/id o)})} "View receipt")))

                          (dom/div
                            (css/add-classes [:section-content :sl-order-card-content])
                            (menu/vertical
                              (css/add-classes [:sl-order-items-list])
                              (map (fn [oi]
                                     (let [sku (:order.item/parent oi)
                                           product (:store.item/_skus sku)]
                                       (menu/item
                                         nil
                                         ;(dom/a
                                         ;  nil)
                                         (dom/a
                                           (css/add-class :sl-order-items-list-item--info)
                                           (dom/div
                                             (css/add-class :info-photo)
                                             (photo/product-preview product {:transformation :transformation/thumbnail}))
                                           (dom/p nil (dom/small nil (:store.item/name product))
                                                  (dom/br nil)
                                                  (dom/small nil (:store.item.sku/variation sku))))
                                         (dom/div
                                           (css/add-classes [:shrink :sl-order-items-list-item--price])
                                           (dom/small nil (ui-utils/two-decimal-price (:store.item/price product))))
                                         )))
                                   (filter #(= (:order.item/type %) :order.item.type/sku) (:order/items o))))

                            (dom/div
                              (css/add-class :sl-order-card-subtitle)
                              (dom/p (css/text-align :right) (dom/strong nil (str "Total: " (ui-utils/two-decimal-price (:order/amount o))))))
                            ;(dom/p nil (dom/span nil (str (count (filter #(= (:order.item/type %) :order.item.type/sku) (:order/items o))) " items")))
                            ;(dom/div
                            ;  (css/add-class :sl-order-card-content--status)
                            ;  (dom/div
                            ;    nil
                            ;    (dom/label nil "Status: ") (dom/span nil (str (string/capitalize (name (:order/status o))))))
                            ;  (dom/div
                            ;    (css/add-class :sl-order-card-title--number)
                            ;    (button/user-setting-default {:href (routes/url :user/order {:order-id (:db/id o)})} "View receipt")))
                            ))))
                    os)])
               orders-by-month))))))

(def ->OrderList (om/factory OrderList))