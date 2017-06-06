(ns eponai.common.ui.user.order-receipt
  (:require
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.utils :as ui-utils]
    [eponai.web.ui.button :as button]
    [eponai.common.ui.common :as common]
    [eponai.client.routes :as routes]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.table :as table]
    [eponai.common.format.date :as date]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.web.ui.photo :as photo]
    [clojure.string :as string]))

(defn store-element [s]
  (let [{:store/keys                      [photo]
         {store-name :store.profile/name} :store.profile} s]
    (dom/div
      nil
      (photo/store-photo s {:transformation :transformation/thumbnail-tiny})
      (dom/div
        (css/text-align :center)
        (dom/p nil (dom/strong
                     (css/add-class :store-name) store-name))))))

(defn order-element [component order]
  (let [{:order/keys [store created-at items amount status]
         order-id    :db/id} order
        {store-name :store.profile/name} (:store/profile store)]
    (dom/div
      (css/add-class :sl-order-card)
      ;(photo/store-cover store nil)
      (dom/div
        (css/add-classes [:section-title :sl-order-card-title])
        (dom/a
          (css/add-class :sl-order-card-title--store {:href (routes/url :store {:store-id (:db/id store)})})
          (photo/store-photo store {:transformation :transformation/thumbnail-tiny})
          (dom/p nil
                 (dom/span nil (str store-name))
                 (dom/br nil)
                 (dom/small nil (date/date->string created-at "MMMM dd, YYYY"))))
        (dom/div
          (css/add-class :sl-order-card-title--number)
          (button/user-setting-default {:href (routes/url :user/order {:order-id order-id})} "View order")))

      (dom/div
        (css/add-classes [:section-content :sl-order-card-content])
        (menu/vertical
          (css/add-classes [:sl-order-items-list])
          (map (fn [oi]
                 (let [sku (:order.item/parent oi)
                       product (:store.item/_skus sku)]
                   (menu/item
                     nil
                     (dom/a
                       nil
                       (dom/div
                         (css/add-class :sl-order-items-list-item--info)
                         (dom/div
                           (css/add-class :info-photo)
                           (photo/product-preview product {:transformation :transformation/thumbnail}))
                         (dom/p nil (dom/small nil (:store.item/name product))
                                (dom/br nil)
                                (dom/small nil (:store.item.sku/variation sku))))
                       (dom/div
                         (css/add-classes [:shrink :sl-order-items-list-item--price])
                         (dom/small nil (ui-utils/two-decimal-price (:store.item/price product))))))))
               (filter #(= (:order.item/type %) :order.item.type/sku) items)))

        (dom/div
          (css/add-class :sl-order-card-subtitle)
          (dom/p (css/text-align :right) (dom/strong nil (str "Total: " (ui-utils/two-decimal-price amount)))))
        ;(dom/p nil (dom/span nil (str (count (filter #(= (:order.item/type %) :order.item.type/sku) (:order/items o))) " items")))
        (dom/div
          (css/add-class :sl-order-card-content--status)
          (dom/div
            nil
            (dom/label nil "Status: ") (dom/span nil (str (string/capitalize (name status))))))
        ))))

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
                                                                            :store.item/price
                                                                            {:store.item/photos [{:store.item.photo/photo [:photo/id]}
                                                                                                 :store.item.photo/index]}]}]}]}
                    {:order/shipping [:shipping/name
                                      {:shipping/address [:shipping.address/street
                                                          :shipping.address/postal
                                                          :shipping.address/locality
                                                          :shipping.address/region
                                                          :shipping.address/country]}]}
                    :order/created-at
                    :order/user
                    {:order/charge [:charge/id]}
                    {:order/store [{:store/profile [{:store.profile/photo [:photo/id]}
                                                    {:store.profile/cover [:photo/id]}
                                                    :store.profile/tagline
                                                    :store.profile/name]}]}]}
     {:query/order-payment [:charge/id
                            :charge/source
                            :charge/created
                            :charge/amount]}])
  Object
  (render [this]
    (let [{:query/keys [current-route order order-payment]} (om/props this)
          {:keys [route route-params]} current-route
          {:order/keys [store created-at]} order
          {:store.profile/keys [tagline]
           store-name          :store.profile/name} (:store/profile store)]
      (debug "Order receipt:  " order)
      (debug "Order charge:  " order-payment)
      (dom/div
        {:id "sulo-order-receipt"}
        ;(dom/h1 (css/show-for-sr) "Order receipt")

        (menu/breadcrumbs
          nil
          (menu/item nil (dom/a {:href (routes/url :user/order-list route-params)} (dom/span nil "Purchases")))
          (menu/item nil (dom/span nil "Order")))

        (if (common/is-order-not-found? this)
          (common/order-not-found this (routes/url :user/order-list route-params))
          (grid/row-column
            nil

            (dom/div
              (css/add-class :page-title)
              (photo/store-photo store {:transformation :transformation/thumbnail})
              (dom/p nil (dom/span nil store-name)
                     (when (not-empty tagline)
                       [
                        (dom/br nil)
                        (dom/span nil tagline)])))
            (dom/h1 nil "Order receipt")
            ;(dom/h1 nil (str "Order from " store-name))
            (grid/row
              (grid/columns-in-row {:small 1 :medium 2})
              (grid/column
                nil
                (dom/label nil "Date")
                (dom/p nil (dom/span nil (date/date->string created-at "MMMM dd, YYYY"))))
              (grid/column
                nil
                (dom/label nil "Order number")
                (dom/p nil
                       (dom/span nil (:order-id route-params)))))
            (grid/row
              (grid/columns-in-row {:small 1 :medium 2})
              (grid/column
                nil
                (dom/label nil "Payment")
                (let [{:charge/keys [source]} order-payment]
                  (dom/p nil
                         (dom/strong nil (:brand source))
                         (dom/small nil (str " ending in " (:last4 source)))
                         (dom/br nil)
                         (dom/small nil (str "Charged on " (date/date->string (* 1000 (:charge/created order-payment)) "MMMM dd, YYYY"))))))
              (grid/column
                nil
                (dom/label nil "Shipping")
                (let [{:shipping/keys [name address]} (:order/shipping order)]
                  (dom/p nil
                         (dom/strong nil name)
                         (dom/br nil)
                         (dom/span nil (:shipping.address/street address))
                         (dom/br nil)
                         (dom/span nil (string/join ", " (remove nil? [(:shipping.address/city address)
                                                                       (:shipping.address/postal address)
                                                                       (:shipping.address/region address)])))
                         (dom/br nil)
                         (dom/span nil (:shipping.address/country address))))))

            (dom/div
              (css/add-class :section-title)
              (dom/p nil "Order details"))
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
                             (->> (grid/column-size {:small 8 :medium 6})
                                  (css/add-class :order-item-info))
                             (photo/product-preview product {:transformation :transformation/thumbnail})
                             (dom/p nil
                                    (dom/span nil (:store.item/name product))
                                    (dom/br nil)
                                    (dom/small nil (:store.item.sku/variation sku))))
                           ;(grid/column (grid/column-size {:small 4 :medium 3}))
                           (grid/column
                             (->> (grid/column-size {:small 4 :medium 6})
                                  (css/text-align :right)
                                  (css/add-class :order-item-price))
                             (dom/p nil
                                    (ui-utils/two-decimal-price (:store.item/price product))))))))
                   (filter #(= (:order.item/type %) :order.item.type/sku) (:order/items order)))
              (let [shipping (some #(when (= (:order-type %) :order.item.type/shipping) %) (:order/items order))]
                (menu/item nil
                           (grid/row
                             nil
                             (grid/column (grid/column-size {:small 4 :medium 6}))
                             (grid/column
                               (grid/column-size {:small 4 :medium 3})
                               (dom/p nil "Shipping"))
                             (grid/column
                               (->> (grid/column-size {:small 4 :medium 3})
                                    (css/text-align :right))
                               (dom/p nil (ui-utils/two-decimal-price shipping))))
                           (grid/row
                             nil
                             (grid/column (grid/column-size {:small 4 :medium 6}))
                             (grid/column
                               (grid/column-size {:small 4 :medium 3})
                               (dom/p nil "Tax"))
                             (grid/column
                               (->> (grid/column-size {:small 4 :medium 3})
                                    (css/text-align :right))
                               (dom/p nil (ui-utils/two-decimal-price 0)))))))
            (grid/row
              (css/add-class :total-price)
              (grid/column (grid/column-size {:small 4 :medium 6}))
              (grid/column (grid/column-size {:small 4 :medium 3})
                           (dom/strong nil "Total"))
              (grid/column
                (->> (grid/column-size {:small 4 :medium 3})
                     (css/text-align :right))
                (dom/strong nil (ui-utils/two-decimal-price (:charge/amount order-payment)))))
            (dom/div
              (css/add-class :contact)
              (dom/p nil
                     (dom/span nil "Still have questions? Contact the shop at ")
                     (dom/a nil (dom/span nil "milena@aakasha.com"))
                     (dom/span nil ".")))
            ))))))

(def ->Order (om/factory Order))
