(ns eponai.common.ui.store.dashboard
  (:require
    [eponai.client.routes :as routes]
    [eponai.common :as c]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.store.order-edit-form :as oef]
    [eponai.common.ui.store.order-list :as ol]
    [eponai.common.ui.store.product-edit-form :as pef]
    [eponai.common.ui.store.product-list :as pl]
    [eponai.common.ui.store.stream-settings :as ss]
    [eponai.common.ui.dom :as my-dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]))

(defn str->json [s]
  #?(:cljs (cljs.reader/read-string s)
     :clj  (clojure.data.json/read-str s :key-fn keyword)))


(defn find-product [store product-id]
  (let [product-id (c/parse-long product-id)]
    (some #(when (= (:db/id %) product-id) %)
          (:store/items store))))

(defn get-route-params [component]
  (get-in (om/props component) [:query/current-route :route-params]))

(defui Dashboard
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/store [:store/uuid
                    :store/name
                    {:store/owners [{:store.owner/user [:user/email]}]}
                    :store/stripe
                    {:store/items [:store.item/name
                                   :store.item/description
                                   :store.item/price
                                   {:store.item/photos [:photo/path]}
                                   {:store.item/skus [:store.item.sku/price
                                                      :store.item.sku/quantity
                                                      :store.item.sku/value]}]}
                    :store/collections]}
     :query/current-route
     :query/messages
     {:proxy/product-edit (om/get-query pef/ProductEditForm)}
     {:proxy/order-edit (om/get-query oef/OrderEditForm)}
     {:proxy/order-list (om/get-query ol/OrderList)}
     {:proxy/stream-settings (om/get-query ss/StreamSettings)}
     ])
  Object
  (initLocalState [_]
    {:selected-tab :products})
  (render [this]
    (let [{:proxy/keys [product-edit order-edit order-list stream-settings]
           :keys       [proxy/navbar query/store query/current-route]} (om/props this)
          {:keys [route route-params]} current-route]
      (my-dom/div
        (->> {:id "sulo-my-store"}
             (css/add-class :sulo-page))
        (common/page-container
          {:navbar navbar}
          (my-dom/div
            (->> (css/grid-row))
            (my-dom/div
              (->> (css/grid-column))
              (menu/horizontal
                (css/add-class :store-nav)
                ;(menu-item "/products" "Products" (= dashboard-option "products"))
                ;(menu-item "/orders" "Orders" (= dashboard-option "orders"))
                (menu/item-tab {:active? (= route :store-dashboard/product-list)
                                :href    (routes/url :store-dashboard/product-list {:store-id (:db/id store)})}
                               "Products")
                (menu/item-tab {:active? (= route :store-dashboard/order-list)
                                :href    (routes/url :store-dashboard/order-list {:store-id (:db/id store)})}
                               "Orders")
                (menu/item-tab {:active? (= route :store-dashboard/stream)
                                :href    (routes/url :store-dashboard/stream {:store-id (:db/id store)})}
                               "Stream"))))
          (condp = route
            :store-dashboard/order-list (ol/->OrderList (om/computed order-list
                                                                     {:store store}))
            :store-dashboard/order (oef/->OrderEditForm (om/computed order-edit
                                                                     {:route-params route-params}))
            :store-dashboard/create-order (oef/->OrderEditForm (om/computed order-edit
                                                                            {:route-params route-params}))

            :store-dashboard/stream (ss/->StreamSettings (om/computed stream-settings
                                                                      {:store store}))
            :store-dashboard/product-list (pl/->ProductList store)
            :store-dashboard/create-product (pef/->ProductEditForm (om/computed product-edit
                                                                                {:route-params route-params}))
            :store-dashboard/product (pef/->ProductEditForm (om/computed product-edit
                                                                         {:route-params route-params
                                                                          :product      (find-product store (:product-id route-params))}))
            nil))))))

(def ->Dashboard (om/factory Dashboard))
