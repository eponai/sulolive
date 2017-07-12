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
    [eponai.web.ui.button :as button]
    [eponai.common.ui.product-item :as pi]
    #?(:cljs [eponai.web.utils :as web.utils])
    [eponai.web.ui.content-item :as ci]))

(defui OrderList

  static om/IQuery
  (query [_]
    [:query/current-route
     {:query/orders [:db/id
                     :order/uuid
                     :order/status
                     :order/amount
                     {:order/items [:order.item/type
                                    :order.item/amount
                                    :order.item/description
                                    :order.item/title
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
                                                     :store.profile/name]}]}]}
     {:query/featured-items [:db/id
                             :store.item/name
                             :store.item/price
                             :store.item/created-at
                             {:store.item/photos [{:store.item.photo/photo [:photo/path :photo/id]}
                                                  :store.item.photo/index]}
                             {:store/_items [{:store/profile [:store.profile/name]}
                                             :store/locality]}]}
     :query/locations])

  Object
  (render [this]
    (let [{:query/keys [orders current-route featured-items locations]} (om/props this)
          orders-by-month (group-by #(date/month->long (:order/created-at % 0)) orders)]
      (debug "Got orders: " orders)
      (dom/div
        {:id "sulo-user-order-list"}

        (grid/row-column
          nil
          (dom/h1 nil "Purchases")
          (if (not-empty orders)
            (map (fn [[timestamp os]]
                   (dom/div
                     nil
                     (dom/h2 nil (date/date->string timestamp "MMMM YYYY"))
                     (map
                       (fn [o]
                         (let [{:order/keys [store]} o
                               {store-name :store.profile/name} (:store/profile store)
                               skus (filter #(= (:order.item/type %) :order.item.type/sku) (:order/items o))
                               shipping (some #(when (= (:order.item/type %) :order.item.type/shipping) %) (:order/items o))]
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
                                              (dom/p nil (dom/small nil (:order.item/title oi))
                                                     (dom/br nil)
                                                     (dom/small nil (:order.item/description oi))))
                                            (dom/div
                                              (css/add-classes [:shrink :sl-order-items-list-item--price])
                                              (dom/small nil (ui-utils/two-decimal-price (:order.item/amount oi))))
                                            )))
                                      skus))

                               (dom/div
                                 (css/add-class :sl-order-card-subtitle)
                                 (dom/p (css/text-align :right)
                                        (dom/small nil (str "Shipping: " (ui-utils/two-decimal-price (:order.item/amount shipping))))
                                        (dom/br nil)
                                        (dom/strong nil (str "Total: " (ui-utils/two-decimal-price (:order/amount o))))))
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
                       os)))
                 orders-by-month)
            (dom/div
              (css/add-class :empty-container)
              (dom/p (css/add-class :shoutout) "You haven't made any purchases yet")
              ;(dom/br nil)
              (button/button
                (button/sulo-dark (button/hollow {:href (routes/url :browse/all-items {:locality (:sulo-locality/path locations)})})) (dom/span nil "Browse products")))))

        (when (some? locations)
          [(grid/row-column
             nil
             (dom/hr nil)
             (dom/div
               (css/add-class :section-title)
               (dom/h3 nil (str "New arrivals in " (:sulo-locality/title locations)))))
           (grid/row
             (->>
               (grid/columns-in-row {:small 2 :medium 3 :large 5}))
             (map
               (fn [p]
                 (grid/column
                   (css/add-class :new-arrival-item)
                   (ci/->ProductItem p)))
               (take 5 featured-items)))])
        ))))

(def ->OrderList (om/factory OrderList))