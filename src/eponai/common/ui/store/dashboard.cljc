(ns eponai.common.ui.store.dashboard
  (:require
    [eponai.client.routes :as routes]
    [eponai.common :as c]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.store.account :as as]
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
    [eponai.common.ui.elements.photo :as photo]
    [medley.core :as medley]))

(defn str->json [s]
  #?(:cljs (cljs.reader/read-string s)
     :clj  (clojure.data.json/read-str s :key-fn keyword)))


(defn find-product [store product-id]
  (let [product-id (c/parse-long product-id)]
    (some #(when (= (:db/id %) product-id) %)
          (:store/items store))))

(defn get-route-params [component]
  (get-in (om/props component) [:query/current-route :route-params]))

(defn parse-route [route]
  (let [ns (namespace route)
        subroute (name route)
        path (clojure.string/split subroute #"#")]
    (keyword ns (first path))))

(defn sub-navbar [component]
  (let [{:query/keys [current-route store]} (om/props component)
        {:keys [route]} current-route
        store-id (:db/id store)
        nav-breakpoint :medium]
    (dom/div #js {:className "navbar-container" :id "store-navbar"}
      (dom/nav #js {:className "top-bar navbar"}
               (menu/horizontal
                 nil
                 (menu/item (when (= route :store-dashboard)
                              (css/add-class ::css/is-active))
                            (my-dom/a
                              (css/add-class :category {:href (routes/url :store-dashboard {:store-id store-id})})
                              (my-dom/span (css/show-for nav-breakpoint) "Dashboard")
                              (my-dom/i
                                (css/hide-for nav-breakpoint {:classes [:fa :fa-dashboard :fa-fw]}))))
                 (menu/item (when (= route :store-dashboard/stream)
                              (css/add-class ::css/is-active))
                            (my-dom/a
                              (css/add-class :category {:href (routes/url :store-dashboard/stream {:store-id store-id})})
                              (my-dom/span (css/show-for nav-breakpoint) "Stream")
                              (my-dom/i
                                (css/hide-for nav-breakpoint {:classes [:fa :fa-video-camera :fa-fw]}))))
                 (menu/item
                   (when (or (= route :store-dashboard/product-list)
                             (= route :store-dashboard/product)
                             (= route :store-dashboard/create-product))
                     (css/add-class ::css/is-active))
                   (my-dom/a
                     (css/add-class :category {:href (routes/url :store-dashboard/product-list {:store-id store-id})})
                     (my-dom/span (css/show-for nav-breakpoint) "Products")
                     (my-dom/i
                       (css/hide-for nav-breakpoint {:classes [:fa :fa-gift :fa-fw]}))))
                 (menu/item
                   (when (or (= route :store-dashboard/order-list)
                             (= route :store-dashboard/order))
                     (css/add-class ::css/is-active))
                   (my-dom/a
                     (css/add-class :category {:href (routes/url :store-dashboard/order-list {:store-id store-id})})
                     (my-dom/span (css/show-for nav-breakpoint) "Orders")
                     (my-dom/i
                       (css/hide-for nav-breakpoint {:classes [:fa :fa-file-text-o :fa-fw]}))))
                 (menu/item
                   (when (= (parse-route route) :store-dashboard/settings)
                     (css/add-class ::css/is-active))
                   (my-dom/a
                     (css/add-class :category {:href (routes/url :store-dashboard/settings {:store-id store-id})})
                     (my-dom/span (css/show-for nav-breakpoint) "Settings")
                     (my-dom/i
                       (css/hide-for nav-breakpoint {:classes [:fa :fa-video-camera :fa-fw]})))))))))

(def compute-route-params #(select-keys % [:route-params]))
(def compute-store #(select-keys % [:store]))

(def route-map {:store-dashboard/order-list     {:component   ol/OrderList
                                                 :computed-fn compute-store}
                :store-dashboard/order          {:component   oef/OrderEditForm
                                                 :computed-fn compute-route-params}
                :store-dashboard/create-order   {:component   oef/OrderEditForm
                                                 :computed-fn (fn [{:keys [store route-params]}]
                                                                {:route-params route-params
                                                                 :products     (:store/items store)})}
                :store-dashboard/stream         {:component   ss/StreamSettings
                                                 :computed-fn compute-store}
                :store-dashboard/settings       {:component   as/AccountSettings
                                                 :computed-fn compute-store}
                :store-dashboard/product-list   {:component   pl/ProductList
                                                 :computed-fn compute-route-params}
                :store-dashboard/create-product {:component   pef/ProductEditForm
                                                 :computed-fn compute-route-params}
                :store-dashboard/product        {:component   pef/ProductEditForm
                                                 :computed-fn (fn [{:keys [store route-params]}]
                                                                {:route-params route-params
                                                                 :product      (find-product store (:product-id route-params))})}})

(defui Dashboard
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/store [:db/id
                    :store/uuid
                    :store/name
                    {:store/owners [{:store.owner/user [:user/email]}]}
                    {:store/photo [:photo/path]}
                    :store/stripe
                    :store/description
                    :store/return-policy
                    :store/tagline
                    {:store/items [:store.item/uuid
                                   :store.item/name
                                   :store.item/description
                                   :store.item/price
                                   {:store.item/photos [:photo/path]}
                                   {:store.item/skus [:store.item.sku/uuid
                                                      :store.item.sku/quantity
                                                      :store.item.sku/variation]}]}
                    :store/collections
                    {:stream/_store [:stream/state]}]}
     :query/current-route
     :query/messages
     {:routing/store-dashboard (-> (medley/map-vals (comp om/get-query :component) route-map)
                                   (assoc :store-dashboard []))}
     {:query/stripe-account [:stripe/legal-entity :stripe/verification]}
     ])
  Object
  (initLocalState [_]
    {:selected-tab :products})
  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [store current-route stripe-account]
           :as         props} (om/props this)
          {:keys [route route-params]} current-route
          store-id (:db/id store)
          ;; Implement a :query/stream-by-store-id ?
          stream-state (-> store :stream/_store first :stream/state)]

      (common/page-container
        {:navbar navbar
         :id     "sulo-store-dashboard"}
        (sub-navbar this)
        (if-not (= route :store-dashboard)
          ;; Dispatch on the routed component:
          (let [{:keys [component computed-fn factory]} (get route-map (parse-route route))
                factory (or factory (om/factory component))
                routed-props (:routing/store-dashboard props)]
            (factory (om/computed routed-props (computed-fn (assoc props :store store :route-params route-params)))))
          ;; Render the store's dashboard:
          (my-dom/div
            (->> (css/grid-row {:id "sulo-main-dashboard"})
                 (css/align :center))
            (my-dom/div
              (->> (css/grid-column)
                   (css/grid-column-size {:small 10 :medium 4 :large 3}))
              (my-dom/div
                (->> (css/add-class ::css/callout)
                     (css/add-class :profile-photo-container))
                (photo/store-photo store)
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
                        (if (or (nil? stream-state) (= stream-state :stream.state/offline))
                          (dom/div #js {:className "sulo-stream-status offline"}
                            (dom/i #js {:className "fa fa-circle fa-2x"})
                            ;(dom/span nil "You are currently")
                            (dom/span nil "You are currently OFFLINE"))
                          (dom/div #js {:className "sulo-stream-status online"}
                            (dom/i #js {:className "fa fa-circle fa-2x"})
                            ;(dom/span nil "You are currently")
                            (dom/span nil (condp = stream-state
                                            :stream.state/live "You are currently LIVE"
                                            :stream.state/online "All set to go live")))))
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
