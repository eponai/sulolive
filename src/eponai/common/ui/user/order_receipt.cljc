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
                         (dom/p nil (dom/small nil (:order.item/title oi))
                                (dom/br nil)
                                (dom/small nil (:store.item.sku/variation sku))))
                       (dom/div
                         (css/add-classes [:shrink :sl-order-items-list-item--price])
                         (dom/small nil (ui-utils/two-decimal-price (:store.item/price product))))))))
               (filter #(= (:order.item/type %) :order.item.type/sku) items)))

        (dom/div
          (css/add-class :sl-order-card-subtitle)
          (dom/p (css/text-align :right) (dom/strong nil (str "Total: " (ui-utils/two-decimal-price amount)))))
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
                                   :order.item/amount
                                   :order.item/description
                                   :order.item/title
                                   {:order.item/parent [{:store.item/_skus [:store.item/name
                                                                            :store.item/price
                                                                            {:store.item/photos [{:store.item.photo/photo [:photo/id]}
                                                                                                 :store.item.photo/index]}]}]}]}
                    {:order/shipping [:shipping/name
                                      {:shipping/address [:shipping.address/street
                                                          :shipping.address/postal
                                                          :shipping.address/locality
                                                          :shipping.address/region
                                                          {:shipping.address/country [:country/name :country/code]}]}]}
                    :order/created-at
                    :order/user
                    {:order/charge [:db/id :charge/id]}
                    {:order/store [{:store/profile [{:store.profile/photo [:photo/id]}
                                                    {:store.profile/cover [:photo/id]}
                                                    :store.profile/email
                                                    :store.profile/tagline
                                                    :store.profile/name]}
                                   {:store/owners [{:store.owner/user [:user/email]}]}]}]}
     {:query/auth [:user/email]}
     {:query/order-payment [:charge/id
                            :charge/source
                            :charge/created
                            :charge/amount
                            :charge/amount-refunded]}])
  Object
  (render [this]
    (let [{:query/keys [current-route order order-payment auth]} (om/props this)
          {:keys [route route-params]} current-route
          {:order/keys [store created-at]} order
          {:store.profile/keys [tagline]
           store-name          :store.profile/name} (:store/profile store)
          delivery (some #(when (= (:order.item/type %) :order.item.type/shipping) %) (:order/items order))
          discount (some #(when (= (:order.item/type %) :order.item.type/discount) %) (:order/items order))
          tax-item (some #(when (= (:order.item/type %) :order.item.type/tax) %) (:order/items order))
          skus (filter #(= (:order.item/type %) :order.item.type/sku) (:order/items order))]
      (debug "Order receipt:  " order)
      (debug "Order charge:  " order-payment)
      (dom/div
        {:id "sulo-order-receipt"}

        (grid/row-column
          (css/add-class :go-back)
          (dom/a
            {:href (routes/url :user/order-list (:route-params current-route))}
            (dom/span nil "Back to purchases")))

        (if (common/is-order-not-found? this)
          (common/order-not-found this (routes/url :user/order-list route-params))
          (grid/row-column
            nil
            (when (and (some? (:charge/amount-refunded order-payment)) (pos? (:charge/amount-refunded order-payment)))
              (dom/h3 (css/add-class :text-alert) (str (ui-utils/two-decimal-price (:charge/amount-refunded order-payment)) " refunded")))

            (callout/callout
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
                           (dom/br nil)
                           (dom/span nil (str "ending in " (:last4 source)))
                           (dom/br nil)
                           (dom/small nil (str "Charged on " (date/date->string (* 1000 (:charge/created order-payment)) "MMMM dd, YYYY"))))))
                )
              (grid/row
                (grid/columns-in-row {:small 1 :medium 2})
                (grid/column
                  nil
                  (dom/label nil "Ship to")
                  (let [{:shipping/keys [name address]} (:order/shipping order)]
                    (common/render-shipping (:order/shipping order) nil)))
                (grid/column
                  nil
                  (dom/label nil "Delivery")
                  (dom/p nil (dom/strong nil (:order.item/title delivery))
                         (dom/br nil)
                         (dom/small nil (:order.item/description delivery))))
                )


              (dom/div
                (css/add-class :section-title)
                (dom/label nil "Details"))
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
                                      (dom/span nil (:order.item/title oi))
                                      (dom/br nil)
                                      (dom/small nil (:order.item/description oi))))
                             (grid/column
                               (->> (grid/column-size {:small 4 :medium 6})
                                    (css/text-align :right)
                                    (css/add-class :order-item-price))
                               (dom/p nil
                                      (ui-utils/two-decimal-price (:order.item/amount oi))))))))
                     skus)

                (menu/item nil
                           (map (fn [{:keys [title value neg? description]}]
                                  (grid/row
                                    nil
                                    (grid/column (grid/column-size {:small 4 :medium 6})
                                                 (dom/small nil (str description)))
                                    (grid/column
                                      (grid/column-size {:small 4 :medium 3})
                                      (dom/p nil (dom/small nil title)))
                                    (grid/column
                                      (->> (grid/column-size {:small 4 :medium 3})
                                           (css/text-align :right))
                                      (dom/p nil (dom/small nil (if neg?
                                                                  (str "-" (ui-utils/two-decimal-price value))
                                                                  (ui-utils/two-decimal-price value)))))))
                                [{:title "Subtotal" :value (apply + (map :order.item/amount skus))}
                                 {:title "Discount" :value (:order.item/amount discount) :neg? true :description (:order.item/description discount)}
                                 {:title "Shipping" :value (:order.item/amount delivery) :description (:order.item/title delivery)}
                                 {:title "Tax" :value (:order.item/amount tax-item)}])))
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
                (let [store-email (or (get-in store [:store/profile :store.profile/email])
                                      (get-in store [:store/owners :store.owner/user :user/email]))]
                  (dom/p nil
                         (dom/span nil "Still have questions? Contact the shop at ")
                         (dom/a {:href (when store-email (str "mailto:" store-email "?subject=SULO Live order #" (:db/id order)))} (dom/span nil store-email))
                         (dom/span nil ".")))))))
        (grid/row-column
          (css/add-class :go-back)
          (dom/a
            {:href (routes/url :user/order-list (:route-params current-route))}
            (dom/span nil "Back to purchases")))))))

(def ->Order (om/factory Order))
