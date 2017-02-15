(ns eponai.common.ui.store.order-edit-form
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.common :as common]
    #?(:cljs
       [eponai.web.utils :as utils])
    [eponai.client.parser.message :as msg]
    [eponai.common.database :as db]))

(def form-elements
  {:input-currency "oef-input-currency"
   :input-price "oef-input-price"})

(defn get-route-params [component]
  (get (om/get-computed component) :route-params))

(defui OrderEditForm
  static om/IQuery
  (query [_]
    [:query/messages
     :query/order])
  Object
  #?(:cljs
     (create-order [this]
       (let [{:keys [order-id store-id action]} (get-route-params this)
             order {:currency (utils/input-value-by-id (:input-currency form-elements))
                    :amount (utils/input-value-by-id (:input-price form-elements))}]

         (cond (some? order-id)
               (msg/om-transact! this `[(store/update-order ~{:order    order
                                                              :order-id order-id
                                                              :store-id store-id})
                                        :query/store])

               (= action "create")
               (msg/om-transact! this `[(store/create-order
                                          ~{:order    order
                                            :store-id store-id})
                                        :query/orders])))))
  (render [this]
    (let [{:keys [query/order]} (om/props this)
          {:order/keys [id amount currency]} order
          is-loading? false]
      (dom/div nil
        #?(:clj (common/loading-spinner nil))
        (my-dom/div
          (->> (css/grid-row))
          (my-dom/div
            (css/grid-column)
            (if order
              (dom/h2 nil "Edit Order - " (dom/small nil (:order/id order)))
              (dom/h2 nil "New Order")))

          (when order
            (my-dom/div
              (->> (css/grid-column)
                   (css/text-align :right))
              (dom/a #js {:className "button hollow"
                          :onClick   #(.delete-order this)} "Delete"))))

        (my-dom/div (->> (css/grid-row)
                         (css/grid-column))
                    (dom/div #js {:className "callout transparent"}
                      (dom/h4 nil (dom/span nil "Details"))
                      (my-dom/div (->> (css/grid-row)
                                       (css/grid-column))
                                  (dom/label nil "Currency")
                                  (my-dom/input {:id           (get form-elements :input-currency)
                                                 :type         "text"
                                                 :defaultValue (or currency "")}))
                      (my-dom/div (->> (css/grid-row))
                                  (my-dom/div
                                    (->> (css/grid-column)
                                         (css/grid-column-size {:medium 3}))
                                    (dom/label nil "Price")
                                    (my-dom/input {:id           (get form-elements :input-price)
                                                   :type         "number"
                                                   :step         "0.01"
                                                   :min          0
                                                   :max          "99999999.99"
                                                   :defaultValue (or amount "")})))))

        ;(my-dom/div
        ;  (->> (css/grid-row)
        ;       css/grid-column)
        ;  (dom/div #js {:className "callout transparent"}
        ;    (dom/h4 nil "Images")))

        ;(my-dom/div (->> (css/grid-row)
        ;                 (css/grid-column))
        ;            (dom/div #js {:className "callout transparent"}
        ;              (dom/h4 nil (dom/span nil "Inventory"))))
        ;(my-dom/div (->> (css/grid-row)
        ;                 (css/grid-column)))
        (my-dom/div (->> (css/grid-row)
                         (css/grid-column))
                    (dom/div nil
                      (dom/a #js {:className "button hollow"} (dom/span nil "Cancel"))
                      (dom/a #js {:className "button"
                                  :onClick   #(when-not is-loading? (.create-order this))}
                             (if is-loading?
                               (dom/i #js {:className "fa fa-spinner fa-spin"})
                               (dom/span nil "Save")))))))))

(def ->OrderEditForm (om/factory OrderEditForm))

