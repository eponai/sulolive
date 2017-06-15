(ns eponai.common.ui.store.order-list
  (:require
    [eponai.client.routes :as routes]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.table :as table]
    [eponai.common.ui.utils :as ui-utils]
    [eponai.common.ui.common :as common]
    [om.next :as om :refer [defui]]
    [eponai.common.format.date :as date]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.grid :as grid]))

(defn filter-new-orders [orders]
  (filter #(#{:order.status/created :order.status/paid} (:order/status %)) orders))

(defui OrderList
  static om/IQuery
  (query [_]
    [{:query/orders [:order/store
                     :order/uuid
                     :order/status
                     {:order/items [:order.item/amount :order.item/description :order.item/type]}
                     {:order/shipping [:shipping/name]}
                     :order/amount
                     :order/created-at]}
     :query/current-route])

  Object
  (initLocalState [_]
    {:selected-tab :inbox})
  (componentDidMount [this]
    (let [{:keys [query/orders query/current-route]} (om/props this)
          new-orders (filter-new-orders orders)]
      (when (empty? new-orders)
        (om/update-state! this assoc :selected-tab :all))))
  (render [this]
    (let [{:keys [store]} (om/get-computed this)
          {:keys [query/orders query/current-route]} (om/props this)
          {:keys [search-input selected-tab]} (om/get-state this)
          new-orders (filter-new-orders orders)

          filtered-orders (->> (cond (not-empty search-input)
                                    (filter #(clojure.string/starts-with? (str (:db/id %))
                                                                          search-input) orders)
                                    (= selected-tab :inbox)
                                    new-orders
                                    :else
                                    orders)
                              (sort-by :order/created-at >))]
      (debug "Got orders: " orders)
      (dom/div
        {:id "sl-order-list"}

        (dom/div
          (css/add-class :section-title)
          (dom/h1 nil "Orders"))
        (callout/callout
          (css/add-class :submenu)
          (menu/horizontal
            nil
            (menu/item
              (when (= selected-tab :inbox)
                (css/add-class :is-active))
              (dom/a {:onClick #(om/update-state! this assoc :selected-tab :inbox)}
                     (dom/span nil (str "Inbox (" (count new-orders) ")"))))
            (menu/item
              (when (= selected-tab :all)
                (css/add-class :is-active))
              (dom/a
                {:onClick #(om/update-state! this assoc :selected-tab :all)}
                (dom/span nil "All orders")))
            (menu/item
              (css/add-class :search-input)
              (dom/input {:value       (or search-input "")
                          :placeholder "Search orders..."
                          :type        "text"
                          :onChange    #(om/update-state! this assoc :search-input (.. % -target -value))}))))
        (cond (empty? orders)
              (dom/div
                (css/add-class :empty-container)
                (dom/p (css/add-class :shoutout)
                       "You have no orders yet."))
              (empty? filtered-orders)
              (dom/div
                (css/add-class :empty-container)
                (dom/p (css/add-class :shoutout)
                       "No orders found."))
              :else
              (callout/callout
                nil

                (menu/vertical
                  (->> (css/add-classes [:sl-orderlist :section-list])
                       (css/hide-for :medium))
                  (map (fn [o]
                         (let [sulo-fee (some #(when (= (:order.item/type %) :order.item.type/sulo-fee) %) (:order/items o))
                               destination-amount (- (:order/amount o) (:order.item/amount sulo-fee))
                               product-link (routes/url :store-dashboard/order
                                                        {:store-id (:db/id store)
                                                         :order-id (:db/id o)})
                               ;orderlist-cell (fn [opts & content]
                               ;                 (table/td
                               ;                   (css/add-class :sl-orderlist-cell opts)
                               ;                   content))
                               {:order/keys [shipping]} o]
                           (menu/item
                             (css/add-class :sl-orderlist-row)
                             (dom/a
                               {:href product-link}
                               (grid/row
                                 nil
                                 (grid/column
                                   nil
                                   (dom/strong nil (:shipping/name shipping)))
                                 (grid/column
                                   (css/text-align :right)
                                   (common/order-status-element o)))
                               (grid/row
                                 (css/align :middle)
                                 (grid/column
                                   nil
                                   (dom/p nil
                                          (dom/small (css/add-class :date) (date/date->string (:order/created-at o) "MM/dd/YYYY"))
                                          (dom/br nil)
                                          (dom/small nil (ui-utils/two-decimal-price destination-amount))))
                                 (grid/column
                                   (css/add-class :shrink)
                                   (dom/i {:classes ["fa fa-chevron-right"]})))))))
                       filtered-orders))
                (table/table
                  (->> (css/add-class :hover (css/add-class :sl-orderlist))
                       (css/show-for :medium))
                  (table/thead
                    nil
                    (table/thead-row
                      nil
                      ;(table/th (css/show-for :medium) "")
                      (table/th nil (dom/span nil "#"))
                      (table/th nil (dom/span nil "Date"))
                      (table/th nil (dom/span nil "Name"))
                      (table/th (css/text-align :right) (dom/span nil "Status"))
                      (table/th (css/text-align :right) (dom/span nil "Amount"))
                      ;(table/th
                      ;  (css/show-for :medium) "Last Updated")
                      ))
                  (table/tbody
                    nil
                    (map
                      (fn [o]
                        (let [sulo-fee (some #(when (= (:order.item/type %) :order.item.type/sulo-fee) %) (:order/items o))
                              destination-amount (- (:order/amount o) (:order.item/amount sulo-fee))
                              product-link (routes/url :store-dashboard/order
                                                       {:store-id (:db/id store)
                                                        :order-id (:db/id o)})
                              orderlist-cell (fn [opts & content]
                                               (table/td
                                                 (css/add-class :sl-orderlist-cell opts)
                                                 content))
                              {:order/keys [shipping]} o]
                          (table/tbody-link-row
                            (->> {:href product-link}
                                 (css/add-class :sl-orderlist-row)
                                 (css/add-class (str "sl-orderlist-row--" (name (:order/status o)))))
                            (orderlist-cell (css/add-class :sl-orderlist-cell--id) (dom/span nil (str "#" (:db/id o))))
                            (orderlist-cell (css/add-class :sl-orderlist-cell--date) (dom/span nil (date/date->string (:order/created-at o) "MM/dd/YYYY")))
                            (orderlist-cell (css/add-class :sl-orderlist-cell--name) (dom/span nil (:shipping/name shipping)))
                            ;(orderlist-cell
                            ;  (->> (css/add-class :sl-orderlist-cell--icon)
                            ;       (css/show-for :medium)) (dom/i {:classes ["fa fa-opencart fa-fw"]}))
                            (orderlist-cell (css/add-class :sl-orderlist-cell--status) (common/order-status-element o))
                            (orderlist-cell (css/add-class :sl-orderlist-cell--price) (ui-utils/two-decimal-price destination-amount))
                            ;(orderlist-cell
                            ;  (->> (css/add-class :sl-orderlist-cell--updated)
                            ;       (css/show-for :medium)) (date/date->string (* 1000 (:order/updated o 0))))
                            )))
                      filtered-orders)))))))))

(def ->OrderList (om/factory OrderList))