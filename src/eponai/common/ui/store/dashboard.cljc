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
    [taoensso.timbre :refer [debug]]
    [eponai.common.format :as f]))

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
                    {:store/items [:store.item/uuid
                                   :store.item/name
                                   :store.item/description
                                   :store.item/price
                                   {:store.item/photos [:photo/path]}
                                   {:store.item/skus [:store.item.sku/uuid
                                                      :store.item.sku/quantity
                                                      :store.item.sku/value]}]}
                    :store/collections]}
     :query/current-route
     :query/messages
     {:proxy/product-list (om/get-query pl/ProductList)}
     {:proxy/product-edit (om/get-query pef/ProductEditForm)}
     {:proxy/order-edit (om/get-query oef/OrderEditForm)}
     {:proxy/order-list (om/get-query ol/OrderList)}
     {:proxy/stream-settings (om/get-query ss/StreamSettings)}
     ])
  Object
  (initLocalState [_]
    {:selected-tab :products})
  (render [this]
    (let [{:proxy/keys [product-edit product-list order-edit order-list stream-settings]
           :keys       [proxy/navbar query/store query/current-route]} (om/props this)
          {:keys [route route-params]} current-route]
      (common/page-container
        {:navbar navbar
         :id     "sulo-store-dashboard"}
        (condp = route
          :store-dashboard/order-list (ol/->OrderList (om/computed order-list
                                                                   {:store store}))
          :store-dashboard/order (oef/->OrderEditForm (om/computed order-edit
                                                                   {:route-params route-params}))
          :store-dashboard/create-order (oef/->OrderEditForm (om/computed order-edit
                                                                          {:route-params route-params
                                                                           :products     (:store/items store)}))

          :store-dashboard/stream (ss/->StreamSettings (om/computed stream-settings
                                                                    {:store store}))
          :store-dashboard/product-list (pl/->ProductList (om/computed product-list
                                                                       {:route-params route-params}))
          :store-dashboard/create-product (pef/->ProductEditForm (om/computed product-edit
                                                                              {:route-params route-params}))
          :store-dashboard/product (pef/->ProductEditForm (om/computed product-edit
                                                                       {:route-params route-params
                                                                        :product      (find-product store (:product-id route-params))}))
          nil)))))

(def ->Dashboard (om/factory Dashboard))
