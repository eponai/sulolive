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
     {:query/my-store [:store/name
                      {:store/owners [{:store.owner/user [:user/email]}]}
                      :store/stripe
                      :store/items
                      :store/collections]}
     :query/stripe])
  Object
  (initLocalState [_]
    {:selected-tab :products})
  (render [this]
    (let [{:keys [selected-tab]} (om/get-state this)
          {:keys [proxy/navbar]} (om/props this)]
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
              (dom/a #js {:className "button"
                          :onClick   #(om/transact! this `[(stripe/create-account)])} "Start my store"))
            ))))))

(def ->YourStore (om/factory YourStore))
