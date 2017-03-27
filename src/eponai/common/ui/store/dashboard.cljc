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
    [om.dom :as dom]
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
          {:keys [route route-params]} current-route
          store-id (:db/id store)]
      (common/page-container
        {:navbar navbar
         :id     "sulo-store-dashboard"}
        (dom/div #js {:className "navbar-container" :id "store-navbar"}
          (dom/nav #js {:className "top-bar navbar"}
                   (menu/horizontal
                     nil
                     (menu/item (when (= route :store-dashboard)
                                  (css/add-class ::css/is-active))
                                (my-dom/a
                                  (css/add-class :category {:href (routes/url :store-dashboard {:store-id store-id})})
                                  (my-dom/span (css/show-for {:size :medium}) "Dashboard")
                                  (my-dom/i
                                    (css/hide-for {:size :medium} {:classes [:fa :fa-dashboard :fa-fw]}))))
                     (menu/item (when (= route :store-dashboard/stream)
                                      (css/add-class ::css/is-active))
                                (my-dom/a
                                  (css/add-class :category {:href (routes/url :store-dashboard/stream {:store-id store-id})})
                                  (my-dom/span (css/show-for {:size :medium}) "Stream")
                                  (my-dom/i
                                    (css/hide-for {:size :medium} {:classes [:fa :fa-video-camera :fa-fw]}))))
                     (menu/item
                       (when (= route :store-dashboard/product-list)
                         (css/add-class ::css/is-active))
                       (my-dom/a
                         (css/add-class :category {:href (routes/url :store-dashboard/product-list {:store-id store-id})})
                         (my-dom/span (css/show-for {:size :medium}) "Products")
                         (my-dom/i
                           (css/hide-for {:size :medium} {:classes [:fa :fa-gift :fa-fw]}))))
                     (menu/item
                       (when (= route :store-dashboard/order-list)
                         (css/add-class ::css/is-active))
                       (my-dom/a
                         (css/add-class :category {:href (routes/url :store-dashboard/order-list {:store-id store-id})})
                         (my-dom/span (css/show-for {:size :medium}) "Orders")
                         (my-dom/i
                           (css/hide-for {:size :medium} {:classes [:fa :fa-file-text-o :fa-fw]})))))))
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
