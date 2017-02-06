(ns eponai.common.ui.store.your-store
  (:require
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.product-item :as pi]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.product :as item]
    [eponai.common.ui.stream :as stream]
    #?(:cljs [eponai.web.utils :as utils])
    [eponai.common.ui.dom :as my-dom]
    [om.dom :as dom]
    [om.next :as om #?(:clj :refer :cljs :refer-macros) [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.menu :as menu]))

(defui YourStore
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/my-store [:store/uuid
                       :store/name
                       {:store/owners [{:store.owner/user [:user/email]}]}
                       :store/stripe
                       {:store/items [:store.item/name]}
                       :store/collections]}
     ;{:query/stripe [:stripe/account :stripe/products]}
     ])
  Object
  (initLocalState [_]
    {:selected-tab :products})
  (render [this]
    (let [{:keys [selected-tab selected-product]} (om/get-state this)
          {:keys [proxy/navbar query/stripe query/my-store]} (om/props this)]
      (debug "My Store: " my-store)
      ;(debug "PRoducts: " stripe)
      (dom/div #js {:id "sulo-my-store" :className "sulo-page"}
        (common/page-container
          {:navbar navbar}
          ;(when (some? selected-product)
          ;  (common/modal {:on-close #(om/update-state! this dissoc :selected-product)}
          ;                (dom/h4 nil (:store.item/name selected-product))
          ;                (dom/label nil "Product Name:")
          ;                (dom/input #js {:type "text"
          ;                                :defaultValue (:store.item/name selected-product)})
          ;                (dom/div nil
          ;                  (dom/a #js {:className "button hollow"} (dom/span nil "Cancel"))
          ;                  (dom/a #js {:className "button"} (dom/span nil "Save")))))
          (my-dom/div
            (->> (css/grid-row))
            (my-dom/div
              (->> (css/grid-column))
              (menu/horizontal
                (css/add-class :store-nav)
                (menu/item-tab {:active?  (= selected-tab :products)
                                :on-click #(om/update-state! this assoc :selected-tab :products)} "Products")
                (menu/item-tab {:active?  (= selected-tab :orders)
                                :on-click #(om/update-state! this assoc :selected-tab :orders)} "Orders"))))
          (when (= selected-tab :products)
            (if (some? selected-product)
              (dom/div nil
                (my-dom/div (->> (css/grid-row)
                                 (css/grid-column))
                            (dom/h4 nil "Edit Product"))
                (my-dom/div (->> (css/grid-row)
                                 (css/grid-column))
                            (dom/label nil "Name:")
                            (dom/input #js {:type         "text"
                                            :defaultValue (:store.item/name selected-product)}))
                (my-dom/div (->> (css/grid-row))
                            (my-dom/div (css/grid-column)
                                        (dom/label nil "Price:")
                                        (dom/input #js {:type         "text"
                                                        :defaultValue (:store.item/price selected-product)}))
                            (my-dom/div (css/grid-column)
                                        (dom/label nil "On Sale")
                                        (dom/input #js {:type         "checkbox"}))
                            (my-dom/div (css/grid-column)
                                        (dom/label nil "Sale Price:")
                                        (dom/input #js {:className "disabled"
                                                        :type         "text"
                                                        :disabled true
                                                        :defaultValue (:store.item/price selected-product)})))
                (my-dom/div (->> (css/grid-row)
                                 (css/grid-column))
                            (dom/div nil
                              (dom/a #js {:className "button hollow"
                                          :onClick #(om/update-state! this dissoc :selected-product)} (dom/span nil "Cancel"))
                              (dom/a #js {:className "button"} (dom/span nil "Save")))))
              (dom/div nil
                (my-dom/div
                  (->> (css/grid-row))
                  (my-dom/div
                    (->> (css/grid-column))
                    (dom/a #js {:className "button"
                                :onClick   #(om/transact! this `[(stripe/create-product ~{:product  {:name "Test product"}
                                                                                          :store-id (:db/id my-store)})
                                                                 :query/my-store])} "Add product")))

                (my-dom/div
                  (->> (css/grid-row))
                  (my-dom/div
                    (->> (css/grid-column))
                    (dom/table
                      nil
                      (dom/thead
                        nil
                        (dom/th nil "Product Name")
                        (dom/th nil (dom/span nil "Delete")))
                      (dom/tbody
                        nil
                        (map (fn [p]
                               (dom/tr nil
                                       (dom/td nil
                                               (dom/a #js {:onClick #(om/update-state! this assoc :selected-product p)}
                                                      (dom/span nil (:store.item/name p))))
                                       (dom/td nil
                                               (dom/a #js {:onClick #(om/transact! this `[(stripe/delete-product ~{:product {:db/id (:db/id p)}})
                                                                                          :query/my-store])} (dom/i #js {:className "fa fa-trash fa-fw"})))))
                             (:store/items my-store)))))))))
          ;(my-dom/div
          ;  (->> (css/grid-row))
          ;  (my-dom/div
          ;    (->> (css/grid-column))
          ;    (dom/a #js {:className "button"
          ;                :onClick   #(om/transact! this `[(stripe/create-account)])} "Start my store")))
          )))))

(def ->YourStore (om/factory YourStore))
