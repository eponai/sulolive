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
    (let [{:keys [selected-tab]} (om/get-state this)
          {:keys [proxy/navbar query/stripe query/my-store]} (om/props this)]
      (debug "My Store: " my-store)
      ;(debug "PRoducts: " stripe)
      (dom/div #js {:id "sulo-my-store" :className "sulo-page"}
        (common/page-container
          {:navbar navbar}
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

          (my-dom/div
            (->> (css/grid-row))
            (my-dom/div
              (->> (css/grid-column))
              (map (fn [p]
                     (dom/div nil
                       (dom/strong nil (:store.item/name p))
                       (dom/a #js {:onClick #(om/transact! this `[(stripe/delete-product ~{:product {:db/id (:db/id p)}})
                                                                  :query/my-store])} (dom/i #js {:className "fa fa-trash fa-fw"}))))
                   (:store/items my-store))))
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
              (dom/a #js {:className "button"
                          :onClick   #(om/transact! this `[(stripe/create-account)])} "Start my store"))))))))

(def ->YourStore (om/factory YourStore))
