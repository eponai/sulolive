(ns eponai.common.ui.store.order-list
  (:require
    [eponai.client.routes :as routes]
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.common :as common]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.format.date :as date]))

(defui OrderList
  static om/IQuery
  (query [_]
    [:query/orders])
  Object
  (render [this]
    (let [{:keys [store]} (om/get-computed this)
          {:keys [query/orders]} (om/props this)]
      (dom/div nil

        (my-dom/div
          (->> (css/grid-row))
          (my-dom/div
            (->> (css/grid-column)
                 ;(css/text-align :right)
                 )
            (dom/h3 nil "Orders")
            ;(dom/a #js {:href      (routes/url :store-dashboard/create-order {:store-id (:db/id store)
            ;                                                                  :action "create"})
            ;            :className "button"} "Create Order")
            ))
        (my-dom/div
          (->> (css/grid-row)
               (css/grid-column))
          ;(my-dom/div
          ;  {:className "callout transparent"})
          (my-dom/input {:value       ""
                         :placeholder "Search Orders..."
                         :type        "text"}))

        (my-dom/div
          (->> (css/grid-row))
          (my-dom/div
            (->> (css/grid-column))
            (dom/table
              #js {:className "hover"}
              (dom/thead
                nil
                (dom/tr nil
                        (dom/th nil "Status")
                        (dom/th nil "ID")
                        (dom/th nil "Amount")
                        (dom/th nil "Last Updated")))
              (dom/tbody
                nil
                (map-indexed
                  (fn [i o]
                    (let [product-link (routes/url :store-dashboard/order
                                                   {:store-id (:db/id store)
                                                    :order-id (:db/id o)})]
                      (dom/tr #js {:key (str i)}
                              (dom/td nil
                                      (dom/a #js {:href product-link}
                                             (common/order-status-element (:order/status o))))
                              (dom/td nil
                                      (dom/a #js {:href product-link}
                                             (dom/span nil (:db/id o))))
                              (dom/td nil
                                      (dom/a #js {:href product-link}
                                             (dom/span nil (:order/amount o))))
                              (dom/td nil
                                      (dom/a #js {:href product-link}
                                             (dom/span nil (date/date->string (* 1000 (:order/updated o)))))))))
                  orders)))))))))

(def ->OrderList (om/factory OrderList))