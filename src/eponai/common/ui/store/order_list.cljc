(ns eponai.common.ui.store.order-list
  (:require
    [eponai.client.routes :as routes]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.table :as table]
    [eponai.common.ui.utils :refer [two-decimal-price]]
    [eponai.common.ui.common :as common]
    [eponai.web.ui.store.common :as store-common]
    [om.next :as om :refer [defui]]
    [eponai.common.format.date :as date]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.callout :as callout]))


(defui OrderList
  static om/IQuery
  (query [_]
    [{:query/orders [:order/store :order/uuid :order/status {:order/items [:order.item/amount]} :order/amount]}])

  Object
  (render [this]
    (let [{:keys [store]} (om/get-computed this)
          {:keys [query/orders]} (om/props this)
          {:keys [search-input]} (om/get-state this)

          orders (if (not-empty search-input)
                   (filter #(clojure.string/starts-with? (str (:db/id %))
                                                         search-input) orders)
                   orders)]
      (dom/div
        {:id "sl-order-list"}
        (dom/h1 (css/show-for-sr) "Orders")

        (dom/div
          (css/add-class :section-title)
          (dom/h2 nil "Orders"))
        (callout/callout
          nil
          (dom/input {:value       (or search-input "")
                      :placeholder "Search Orders..."
                      :type        "text"
                      :onChange    #(om/update-state! this assoc :search-input (.. % -target -value))})
          (table/table
            (css/add-class :hover (css/add-class :sl-orderlist))
            (table/thead
              nil
              (table/thead-row
                nil
                (table/th (css/show-for :medium) "")
                (table/th nil "Amount")
                (table/th nil "Status")
                (table/th nil "ID")
                (table/th
                  (css/show-for :medium) "Last Updated")))
            (table/tbody
              nil
              (map
                (fn [o]
                  (let [total-price (reduce + 0 (map :order.item/amount (:order/items o)))
                        product-link (routes/url :store-dashboard/order
                                                 {:store-id (:db/id store)
                                                  :order-id (:db/id o)})
                        orderlist-cell (fn [opts & content]
                                         (table/td
                                           (css/add-class :sl-orderlist-cell opts)
                                           content))]
                    (table/tbody-link-row
                      (->> {:href product-link}
                           (css/add-class :sl-orderlist-row)
                           (css/add-class (str "sl-orderlist-row--" (name (:order/status o)))))
                      (orderlist-cell
                        (->> (css/add-class :sl-orderlist-cell--icon)
                             (css/show-for :medium)) (dom/i {:classes ["fa fa-opencart fa-fw"]}))
                      (orderlist-cell (css/add-class :sl-orderlist-cell--price) (two-decimal-price (:order/amount o)))
                      (orderlist-cell (css/add-class :sl-orderlist-cell--status) (common/order-status-element o))
                      (orderlist-cell (css/add-class :sl-orderlist-cell--id) (dom/span nil (:db/id o)))
                      (orderlist-cell
                        (->> (css/add-class :sl-orderlist-cell--updated)
                             (css/show-for :medium)) (date/date->string (* 1000 (:order/updated o 0)))))))
                orders))))))))

(def ->OrderList (om/factory OrderList))