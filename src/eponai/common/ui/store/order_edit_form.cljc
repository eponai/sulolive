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
    [taoensso.timbre :refer [debug]]))

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
  (initLocalState [_]
    {:items #{}})
  (render [this]
    (let [{:keys [query/order]} (om/props this)
          {:keys [products]} (om/get-computed this)
          {:order/keys [id amount currency]} order
          {:keys [items]} (om/get-state this)
          is-loading? false
          filtered (filter #(contains? (set items) (:db/id %)) products)]
      (debug "Products: " products)
      (debug "Has items: " filtered)
      (debug "Has items: " items)
      (dom/div nil
        #?(:clj (common/loading-spinner nil))
        (my-dom/div
          (->> (css/grid-row))
          (my-dom/div
            (css/grid-column)
            (if order
              (my-dom/h2 nil "Edit Order - " (my-dom/small nil (:order/id order)))
              (my-dom/h2 nil "New Order")))

          (when order
            (my-dom/div
              (->> (css/grid-column)
                   (css/text-align :right))
              (my-dom/a
                (->> {:onClick #(.delete-order this)}
                     css/button-hollow)
                (my-dom/span nil "Delete")))))

        (my-dom/div (->> (css/grid-row)
                         (css/grid-column))
                    (my-dom/div {:className "callout transparent"}
                      (my-dom/h4 nil (dom/span nil "Details"))

                      (my-dom/div (->> (css/grid-row)
                                       (css/grid-column))
                                  (dom/label nil "Product")
                                  (sel/->SelectOne (om/computed {:value   {:label "Hej" :value "test"}
                                                                 :options (mapv (fn [p]
                                                                                  {:label (:store.item/name p)
                                                                                   :value (:db/id p)})
                                                                                products)}
                                                                {:on-change (fn [p]
                                                                              (debug "P: " p)
                                                                              (om/update-state! this update :items conj (:value p)))}))
                                  ;(my-dom/select {:id           (get form-elements :input-product)}
                                  ;               (my-dom/option {:value "test"} "Hej"))
                                  )
                      (dom/table
                        nil
                        (dom/tbody
                          nil
                          (map (fn [it]
                                 (dom/tr
                                   nil
                                   (dom/td
                                     nil
                                     (:store.item/name it))
                                   (dom/td
                                     nil
                                     (:store.item/price it))
                                   (dom/td
                                     nil
                                     (my-dom/a
                                       {:onClick #(om/update-state! this update :items disj (:db/id it))}
                                       "x"))))
                               (filter #(contains? (set items) (:db/id %)) products)))
                        )

                      ;(my-dom/div (->> (css/grid-row)
                      ;                 (css/grid-column))
                      ;            (dom/label nil "Currency")
                      ;            (my-dom/input {:id           (get form-elements :input-currency)
                      ;                           :type         "text"
                      ;                           :defaultValue (or currency "")}))
                      ;(my-dom/div (->> (css/grid-row))
                      ;            (my-dom/div
                      ;              (->> (css/grid-column)
                      ;                   (css/grid-column-size {:medium 3}))
                      ;              (dom/label nil "Price")
                      ;              (my-dom/input {:id           (get form-elements :input-price)
                      ;                             :type         "number"
                      ;                             :step         "0.01"
                      ;                             :min          0
                      ;                             :max          "99999999.99"
                      ;                             :defaultValue (or amount "")})))
                      ))

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
                (my-dom/span nil "Save")))))))))

(def ->OrderEditForm (om/factory OrderEditForm))

