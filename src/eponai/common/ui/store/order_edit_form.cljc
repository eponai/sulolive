(ns eponai.common.ui.store.order-edit-form
  (:require
    #?(:cljs
       [cljsjs.react-select])
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.components.select :as sel]
    #?(:cljs
       [eponai.web.utils :as utils])
    [eponai.client.parser.message :as msg]
    [eponai.common.database :as db]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.format.date :as date]))

(def form-elements
  {:input-currency "oef-input-currency"
   :input-price    "oef-input-price"})

(defn get-route-params [component]
  (get (om/get-computed component) :route-params))

(def modal-order-status-keys
  {:modal/mark-as-fulfilled? :order.status/fulfilled
   :modal/mark-as-returned? :order.status/returned
   :modal/mark-as-canceled? :order.status/canceled})

(def modal-message
  {:modal/mark-as-fulfilled? "Do you want to fulfill items for this order?"
   :modal/mark-as-returned? "Do you want to mark items as returned to customer?"
   :modal/mark-as-canceled? "Do you want to cancel this order?"})

(defn order-status-modal [component]
  (let [{:keys [modal]} (om/get-state component)
        order-status (get modal-order-status-keys modal)
        on-close #(om/update-state! component dissoc :modal)]
    (when modal
      (common/modal
        {:on-close on-close}
        (dom/div nil
          (dom/h4 nil (get modal-message modal))
          (dom/div nil

            (dom/a #js {:onClick   #(do
                                     (on-close)
                                     (.update-order component {:order/status order-status}))
                        :className "button"} "Yes, Shoot!")
            (dom/a #js {:className "button hollow"
                        :onClick   on-close} "Oops, no thanks.")))))))

(defn edit-order [component]
  (let [{:keys [query/order]} (om/props component)]
    (dom/div nil
      (order-status-modal component)

      (my-dom/div
        (->> (css/grid-row)
             (css/align :bottom))
        (my-dom/div
          (css/grid-column)
          (dom/h2 nil "Edit Order - " (dom/small nil (:order/id order)) " " (common/order-status-element (:order/status order))))
        (my-dom/div
          (->> (css/grid-column)
               (css/add-class :shrink)
               (css/text-align :right))
          (when (or (= (:order/status order) :order.status/created)
                    (= (:order/status order) :order.status/paid))
            (dom/a #js {:className "button hollow alert"
                        :onClick   #(om/update-state! component assoc :modal :modal/mark-as-canceled?)} "Cancel Order"))))
      (my-dom/div (->> (css/grid-row)
                       (css/grid-column))
                  (my-dom/div {:className "callout transparent"}
                              (my-dom/h4 nil (dom/span nil "Details"))


                              (menu/vertical
                                nil
                                (menu/item (css/grid-row)
                                           (my-dom/div
                                             (->> (css/grid-column)
                                                  (css/grid-column-size {:small 3 :medium 2})
                                                  (css/text-align :right))
                                             (dom/label nil "ID: "))
                                           (my-dom/div
                                             (->> (css/grid-column))
                                             (dom/span nil (:db/id order))))
                                (menu/item (css/grid-row)
                                           (my-dom/div
                                             (->> (css/grid-column)
                                                  (css/text-align :right)
                                                  (css/grid-column-size {:small 3 :medium 2}))
                                             (dom/label nil "Created: "))
                                           (my-dom/div
                                             (->> (css/grid-column))
                                             (dom/span nil (date/date->string (* 1000 (:order/created order)) "MMM dd yyyy HH:mm"))))
                                (menu/item (css/grid-row)
                                           (my-dom/div
                                             (->> (css/grid-column)
                                                  (css/text-align :right)
                                                  (css/grid-column-size {:small 3 :medium 2}))
                                             (dom/label nil "Email: "))
                                           (my-dom/div
                                             (->> (css/grid-column))
                                             (dom/span nil (:order/email order)))))))
      (my-dom/div (->> (css/grid-row)
                       css/grid-column)
                  (my-dom/div
                    {:className "callout transparent"}
                    (my-dom/div
                      (css/grid-row)
                      (my-dom/div
                        (->> (css/grid-column)
                             (css/grid-column-size {:small 12 :large 6}))
                        (my-dom/div (css/add-class :order-action)
                                    (dom/div nil
                                      (dom/i #js {:className "fa fa-credit-card fa-fw fa-2x"})
                                      (let [order-status (:order/status order)]
                                        (cond (or (= order-status :order.status/paid)
                                                  (= order-status :order.status/fulfilled))
                                              (dom/span nil "Payment Accepted")

                                              (= order-status :order.status/created)
                                              (dom/span nil "Payment Pending")

                                              (= order-status :order.status/returned)
                                              (dom/span nil "Payment Returned"))))))
                      (my-dom/div
                        (->> (css/grid-column)
                             (css/grid-column-size {:small 12 :large 6}))
                        (let [order-status (:order/status order)]
                          (my-dom/div (css/add-class :order-action)
                                      (dom/div nil
                                        (dom/i #js {:className "fa fa-truck fa-fw fa-2x"})
                                        (cond (= order-status :order.status/fulfilled)
                                              (dom/span nil "Items Fulfilled")
                                              :else
                                              (dom/span nil "Fulfill Items")))
                                      (cond
                                        (= order-status :order.status/fulfilled)
                                        (my-dom/a
                                          (->>
                                            (css/button {:onClick #(om/update-state! component assoc :modal :modal/mark-as-returned?)})
                                            (css/add-class :hollow))
                                          "Return Items")
                                        (= order-status :order.status/returned)
                                        (dom/a nil "")
                                        :else
                                        (my-dom/a (cond->> (css/button {:onClick #(om/update-state! component assoc :modal :modal/mark-as-fulfilled?)})
                                                           (not= (:order/status order) :order.status/paid)
                                                           (css/add-class :disabled)) "Fulfill Items")))))))))))

(defn create-order [component]
  (let [{:keys [products]} (om/get-computed component)]
    (my-dom/div
      nil
      (my-dom/div
        (->> (css/grid-row)
             css/grid-column)
        (my-dom/h2 nil "New Order"))
      (my-dom/div
        (->> (css/grid-row)
             (css/grid-column))
        (my-dom/div
          {:className "callout transparent"}
          (my-dom/h4 nil (dom/span nil "Details"))
          (my-dom/div
            (->> (css/grid-row)
                 (css/grid-column))
            (dom/label nil "Product")
            (sel/->SelectOne (om/computed {:value   {:label "Hej" :value "test"}
                                           :options (mapv (fn [p]
                                                            {:label (:store.item/name p)
                                                             :value (:db/id p)})
                                                          products)}
                                          {:on-change (fn [p]
                                                        (debug "P: " p)
                                                        (om/update-state! component update :items conj (:value p)))}))
            ;(my-dom/select {:id           (get form-elements :input-product)}
            ;               (my-dom/option {:value "test"} "Hej"))
            ))))))
(defui OrderEditForm
  static om/IQuery
  (query [_]
    [:query/messages
     {:query/order [:order/items
                    :order/status
                    :order/email
                    {:order/store [:store/name {:store/photo [:photo/path]}]}]}])
  Object
  (create-order [this]
    #?(:cljs
       (let [{:keys [order-id store-id action]} (get-route-params this)
             order {:currency (utils/input-value-by-id (:input-currency form-elements))
                    :amount   (utils/input-value-by-id (:input-price form-elements))}]
         (msg/om-transact! this `[(store/create-order
                                    ~{:order    order
                                      :store-id store-id})
                                  :query/orders]))))

  (update-order [this params]
    (let [{:keys [order-id store-id]} (get-route-params this)]
      (msg/om-transact! this `[(store/update-order ~{:params   params
                                                     :order-id order-id
                                                     :store-id store-id})
                               :query/orders])))
  (initLocalState [_]
    {:items #{}})
  (componentDidMount [this]
    (om/update-state! this assoc :did-mount? true))

  (render [this]
    (let [{:keys [query/order]} (om/props this)
          {:keys [products]} (om/get-computed this)
          {:order/keys [id amount currency items]} order
          {:keys [input-items did-mount?]} (om/get-state this)
          is-loading? false
          filtered (filter #(contains? (set input-items) (:db/id %)) products)
          skus (filter #(= :sku (:order.item/type %)) items)
          tax (some #(when (= :tax (:order.item/type %)) %) items)
          shipping (some #(when (= :shipping (:order.item/type %)) %) items)]
      (dom/div #js {:id "sulo-edit-order"}
        (when-not did-mount?
          (common/loading-spinner nil))
        (if order
          (edit-order this)
          (create-order this))

        (my-dom/div
          (->> (css/grid-row)
               css/grid-column)
          (my-dom/div
            {:className "callout transparent"}
            (my-dom/h4 nil (dom/span nil "Items"))
            (dom/table
              nil
              (dom/thead
                nil
                (dom/tr nil
                        (dom/th nil "Product")
                        (dom/th nil "Description")
                        (dom/th nil "Quantity")
                        (dom/th nil "Price")
                        (dom/th nil "")))
              (dom/tbody
                nil
                (map-indexed
                  (fn [i it]
                       (dom/tr
                         #js {:className "sku"
                              :key (str i)}
                         (dom/td
                           nil
                           (dom/a nil (:order.item/parent it)))
                         (dom/td
                           nil
                           (:order.item/description it))
                         (dom/td
                           nil
                           (:order.item/quantity it))
                         (dom/td
                           nil
                           (:order.item/amount it))
                         (dom/td
                           nil
                           (my-dom/a
                             {:onClick #(om/update-state! this update :items disj (:db/id it))}
                             ""))))
                     skus)
                (when (and tax shipping)
                  (map-indexed (fn [i it]
                         (dom/tr
                           #js {:key (str i)
                                :className (name (:order.item/type it))}
                           (dom/td
                             nil
                             (clojure.string/capitalize (name (:order.item/type it))))
                           (dom/td
                             nil
                             (:order.item/description it))
                           (dom/td
                             nil
                             (:order.item/quantity it))
                           (dom/td
                             nil
                             (:order.item/amount it))
                           (dom/td
                             nil
                             (my-dom/a
                               nil
                               ""))))
                       [tax shipping])))
              (dom/tfoot
                nil
                (dom/tr nil (dom/td nil "Total")
                        (dom/td nil )
                        (dom/td nil )
                        (dom/td nil (:order/amount order))
                        (dom/td nil ))))))

        (when-not order
          (my-dom/div
            (->> (css/grid-row)
                 (css/grid-column))
            (my-dom/div nil
                        (my-dom/a
                          (->> (css/button)
                               css/button-hollow)
                          (my-dom/span nil "Cancel"))
                        (my-dom/a
                          (->> {:onClick #(when-not is-loading? (.create-order this))}
                               (css/button))
                          (if is-loading?
                            (my-dom/i {:className "fa fa-spinner fa-spin"})
                            (my-dom/span nil "Save"))))))))))

(def ->OrderEditForm (om/factory OrderEditForm))

