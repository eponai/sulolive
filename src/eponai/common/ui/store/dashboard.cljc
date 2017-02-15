(ns eponai.common.ui.store.dashboard
  (:require
    [eponai.client.parser.message :as msg]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.common :as common]
    [eponai.client.utils :as client-utils]
    #?(:cljs [eponai.web.utils :as utils])
    #?(:cljs [eponai.client.ui.photo-uploader :as pu])
    [eponai.client.routes :as routes]
    [eponai.common.ui.dom :as my-dom]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common :as c]
    [eponai.common.ui.store.order-list :as ol]
    [eponai.common.ui.store.order-edit-form :as oef]
    [eponai.common.ui.store.product-list :as pl]
    [eponai.common.ui.store.product-edit-form :as pef]))

(defn store-orders [component store opts]
  (my-dom/div (->> (css/grid-row)
                   css/grid-column)
              (dom/div nil "Thes are the orders")))

#?(:cljs
   (defn get-element-by-id [id]
     (.getElementById js/document id)))

#?(:cljs (defn get-input-value [id]
           (when-let [el (get-element-by-id id)]
             (.-value el))))

(defn str->json [s]
  #?(:cljs (cljs.reader/read-string s)
     :clj  (clojure.data.json/read-str s :key-fn keyword)))


(defn find-product [store product-id]
  (let [product-id (c/parse-long product-id)]
    (some #(when (= (:db/id %) product-id) %)
          (:store/items store))))

(defn find-order [store order-id]
  ;(let [order-id (c/parse-long order-id)])
  (some #(when (= (:order/id %) order-id) %)
        (:store/orders store)))

(defn store-stream [component store]
  (let [message (msg/last-message component 'stream-token/generate)]
    (debug ["STREAM_STORE_MESSAGE: " message :componen-state (om/get-state component)])
    (dom/div #js {}
      (dom/input #js {:value (if (msg/final? message)
                               (:token (msg/message message))
                               "Click ->>")})
      (dom/a #js {:className "button"
                  :onClick   #(msg/om-transact! component `[(stream-token/generate ~{:store-id (:db/id store)})])}
             (dom/strong nil "Generate a token!")))))

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
     ])
  Object
  (initLocalState [_]
    {:selected-tab :products})
  (shouldComponentUpdate [this n p]
    #?(:cljs
       (client-utils/shouldComponentUpdate this n p)))
  (render [this]
    (let [{:keys [proxy/navbar query/store query/current-route proxy/product-edit proxy/order-list proxy/order-edit]} (om/props this)
          {:keys [route route-params]} current-route]
      (debug "Dashboard current route: " current-route)
      (dom/div #js {:id "sulo-my-store" :className "sulo-page"}
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

            :store-dashboard/stream (store-stream this store)
            :store-dashboard/product-list (pl/->ProductList store)
            :store-dashboard/create-product (pef/->ProductEditForm (om/computed product-edit
                                                                                {:route-params route-params}))
            :store-dashboard/product (pef/->ProductEditForm (om/computed product-edit
                                                                         {:route-params route-params
                                                                          :product      (find-product store (:product-id route-params))}))
            nil))))))

(def ->Dashboard (om/factory Dashboard))
