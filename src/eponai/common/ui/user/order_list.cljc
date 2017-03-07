(ns eponai.common.ui.user.order-list
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.photo :as photo]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]
    [eponai.client.routes :as routes]))

(defn store-element [s]
  (debug "Store element: " s)
  (let [{:store/keys [photo] store-name :store/name} s]
    (dom/div nil
      (photo/circle {:src (:photo/path photo)})
      (dom/div #js {:className "text-center"} (dom/p nil (dom/strong #js {:className "store-name"} store-name))))))

(defn order-element [component order]
  (let [{:query/keys [current-route]} (om/props component)]
    (let [skus (filter #(= (:order.item/type %) :sku) (:order/items order))
          not-skus (remove #(= (:order.item/type %) :sku) (:order/items order))]
      (dom/div #js {:className "callout"}
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
                              (dom/a nil
                                     (:order.item/parent item)))
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
                      #js {:key i}
                      (dom/td
                        nil)
                      (dom/td nil
                              (dom/span nil (:order.item/description item)))
                      (dom/td nil
                              (dom/span nil (:order.item/amount item)))))
                  not-skus))))
          (my-dom/div
            (->> (css/grid-column)
                 (css/text-align :right)
                 (css/add-class :shrink))
            (dom/a #js {:className "button hollow"
                        :href      (routes/url :user/order {:order-id (:db/id order)
                                                            :user-id  (get-in current-route [:route-params :user-id])})}
                   (dom/span nil "View Order"))))))))

(defui OrderList
  static om/IQuery
  (query [_]
    [:query/current-route
     :query/orders])
  Object
  (render [this]
    (let [{:query/keys [orders current-route]} (om/props this)]
      (dom/div #js {:id "sulo-user-order-list"}
        (my-dom/div
          (->> (css/grid-row)
               css/grid-column)
          (dom/h3 nil "My Orders")
          (map-indexed
            (fn [i o]
              (dom/div nil
                (order-element this o)
                ))
            orders))))))

(def ->OrderList (om/factory OrderList))