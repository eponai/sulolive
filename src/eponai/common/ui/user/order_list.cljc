(ns eponai.common.ui.user.order-list
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.photo :as photo]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]))

(defn store-element [s]
  (debug "Store element: " s)
  (let [{:store/keys [photo] store-name :store/name} s]
    (dom/div nil
      (photo/circle {:src (:photo/path photo)})
      (dom/div #js {:className "text-center"} (dom/p nil (dom/strong #js {:className "store-name"} store-name))))))

(defn order-element [component order]
  (let [skus (filter #(= (:order.item/type %) :sku) (:order/items order))
        not-skus (remove #(= (:order.item/type %) :sku) (:order/items order))]
    (my-dom/div
      (->> (css/grid-row)
           (css/align :bottom))
      (my-dom/div
        (->> (css/grid-column)
             (css/grid-column-size {:small 4 :medium 2}))
        (store-element (:order/store order)))
      (my-dom/div
        (css/grid-column)
        (dom/table
          nil
          (dom/tbody
            nil
            (map-indexed
              (fn [i item]
                (dom/tr #js {:key i :className "order-item-sku"}
                        (dom/td
                          nil
                          (dom/a nil (:order.item/parent item)))
                        (dom/td
                          nil
                          (dom/span nil (:order.item/description item)))
                        (dom/td
                          nil
                          (dom/span nil (:order.item/amount item)))))
              skus))
          (dom/tfoot
            nil
            (map-indexed
              (fn [i item]
                (dom/tr
                  #js {:key (+ i (count skus))}
                  (dom/td
                    nil)
                  (dom/td nil
                          (dom/span nil (:order.item/description item)))
                  (dom/td nil
                          (dom/span nil (:order.item/amount item)))))
              not-skus)))))))

(defui OrderList
  static om/IQuery
  (query [_]
    [:query/orders])
  Object
  (render [this]
    (let [{:query/keys [orders]} (om/props this)]
      (debug "User orders: " orders)
      (dom/div #js {:id "sulo-user-order-list"}
        (my-dom/div
          (->> (css/grid-row)
               css/grid-column)
          (dom/h3 nil "My Orders")
          (dom/div #js {:className "callout"}
            (map-indexed
              (fn [i o]
                (dom/div #js {:key i}
                  (order-element this o)))
              orders)))))))

(def ->OrderList (om/factory OrderList))