(ns eponai.common.ui.store.dashboard
  (:require
    [eponai.client.routes :as routes]
    [eponai.common :as c]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.store.account-settings :as as]
    [eponai.common.ui.store.order-edit-form :as oef]
    [eponai.common.ui.store.order-list :as ol]
    [eponai.common.ui.store.product-edit-form :as pef]
    [eponai.common.ui.store.product-list :as pl]
    [eponai.common.ui.store.stream-settings :as ss]
    [eponai.common.ui.dom :as my-dom]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.format :as f]
    [eponai.common.ui.elements.photo :as photo]))

(defn str->json [s]
  #?(:cljs (cljs.reader/read-string s)
     :clj  (clojure.data.json/read-str s :key-fn keyword)))


(defn find-product [store product-id]
  (let [product-id (c/parse-long product-id)]
    (some #(when (= (:db/id %) product-id) %)
          (:store/items store))))

(defn get-route-params [component]
  (get-in (om/props component) [:query/current-route :route-params]))

(defn sub-navbar [component]
  (let [{:query/keys [current-route store]} (om/props component)
        {:keys [route]} current-route
        store-id (:db/id store)]
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
                 (menu/item (when (= route :store-dashboard/settings)
                              (css/add-class ::css/is-active))
                            (my-dom/a
                              (css/add-class :category {:href (routes/url :store-dashboard/settings {:store-id store-id})})
                              (my-dom/span (css/show-for {:size :medium}) "Settings")
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
                       (css/hide-for {:size :medium} {:classes [:fa :fa-file-text-o :fa-fw]})))))))))

(defui Dashboard
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/store [:store/uuid
                    :store/name
                    {:store/owners [{:store.owner/user [:user/email]}]}
                    {:store/photo [:photo/path]}
                    :store/stripe
                    {:store/items [:store.item/uuid
                                   :store.item/name
                                   :store.item/description
                                   :store.item/price
                                   {:store.item/photos [:photo/path]}
                                   {:store.item/skus [:store.item.sku/uuid
                                                      :store.item.sku/quantity
                                                      :store.item.sku/value]}]}
                    :store/collections
                    {:stream/_store [:stream/status]}]}
     :query/current-route
     :query/messages
     {:proxy/product-list (om/get-query pl/ProductList)}
     {:proxy/product-edit (om/get-query pef/ProductEditForm)}
     {:proxy/order-edit (om/get-query oef/OrderEditForm)}
     {:proxy/order-list (om/get-query ol/OrderList)}
     {:proxy/stream-settings (om/get-query ss/StreamSettings)}
     {:proxy/account-settings (om/get-query as/AccountSettings)}
     {:query/stripe-account [:stripe/legal-entity :stripe/verification]}
     ])
  Object
  (initLocalState [_]
    {:selected-tab :products})
  (render [this]
    (let [{:proxy/keys [navbar product-edit product-list order-edit order-list stream-settings account-settings]
           :query/keys       [store current-route stripe-account]} (om/props this)
          {:keys [route route-params]} current-route
          store-id (:db/id store)]
      (common/page-container
        {:navbar navbar
         :id     "sulo-store-dashboard"}
        (sub-navbar this)
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
          :store-dashboard/settings (as/->AccountSettings (om/computed account-settings
                                                                       {:store store}))
          :store-dashboard/product-list (pl/->ProductList (om/computed product-list
                                                                       {:route-params route-params}))
          :store-dashboard/create-product (pef/->ProductEditForm (om/computed product-edit
                                                                              {:route-params route-params}))
          :store-dashboard/product (pef/->ProductEditForm (om/computed product-edit
                                                                       {:route-params route-params
                                                                        :product      (find-product store (:product-id route-params))}))
          :store-dashboard
          (my-dom/div
            (->> (css/grid-row {:id "sulo-main-dashboard"})
                 (css/align :center))
            (my-dom/div
              (->> (css/grid-column)
                   (css/grid-column-size {:small 10 :medium 4 :large 3}))
              (my-dom/div
                (->> (css/add-class ::css/callout)
                     (css/add-class :profile-photo-container))
                (photo/circle {:src (get-in store [:store/photo :photo/path])})
                (my-dom/div
                  (css/text-align :center)
                  (dom/h4 nil (:store/name store)))
                (my-dom/div
                  (css/add-class :button-container)
                  (dom/a #js {:className "button hollow"
                              :href (routes/url :store {:store-id store-id})} "View Store")
                  (dom/a #js {:className "button hollow"
                              :href (routes/url :store-dashboard/settings {:store-id store-id})} "Edit Store"))))
            (my-dom/div
              (->> (css/grid-column)
                   (css/grid-column-size {:small 12 :medium 8 :large 9}))
              (my-dom/div
                (css/grid-row)
                (my-dom/div
                  (css/grid-column)
                  (my-dom/div
                    (->> {:classes [::css/callout :status-callout ::css/notification]})
                    (my-dom/div
                      (css/grid-row)
                      (my-dom/div
                        (->> (css/grid-column)
                             (css/grid-column-size {:small 12 :medium 6})
                             (css/add-class :stream-status-container))
                        (dom/div #js {:className "sulo-stream-status offline"}
                          (dom/i #js {:className "fa fa-circle fa-2x"})
                          ;(dom/span nil "You are currently")
                          (dom/span nil "You are currently OFFLINE")))
                      (my-dom/div
                        (->> (css/grid-column)
                             (css/text-align :right))
                        (dom/a #js {:className "button highlight hollow"
                                    :href (routes/url :store-dashboard/stream {:store-id store-id})}
                               (dom/span nil "Setup Stream")
                               (dom/i #js {:className "fa fa-chevron-right fa-fw"})))))
                  (when (not-empty (get-in stripe-account [:stripe/verification :stripe.verification/fields-needed]))
                    (my-dom/div
                      (->> (css/add-class ::css/callout)
                           (css/add-class ::css/action)
                           (css/add-class ::css/notification))
                      (my-dom/div
                        (->> (css/grid-row)
                             (css/align :middle))
                        (my-dom/div
                          (css/grid-column)
                          (dom/span nil "Business information needed")
                          (map (fn [n]
                                 (dom/div nil (dom/span nil n)))
                               (get-in stripe-account [:stripe/verification :stripe.verification/fields-needed])))
                        (my-dom/div
                          (->> (css/grid-column)
                               (css/text-align :right))
                          (dom/a #js {:className "button hollow action"
                                      :href (routes/url :store-dashboard/settings {:store-id store-id})}
                                 (dom/span nil "Update Settings")
                                 (dom/i #js {:className "fa fa-chevron-right fa-fw"})))))))))))))))

(def ->Dashboard (om/factory Dashboard))
