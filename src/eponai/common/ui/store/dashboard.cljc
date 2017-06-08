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
    [eponai.common.ui.router :as router]
    [eponai.web.ui.store.common :as store-common]
    [eponai.common.ui.utils :refer [two-decimal-price]]
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.web.ui.store.edit-store :as es]
    [medley.core :as medley]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.format.date :as date]
    [eponai.web.ui.photo :as photo]
    [eponai.web.ui.button :as button]
    [eponai.common.mixpanel :as mixpanel]
    [eponai.web.ui.store.shipping :as shipping]
    [eponai.web.ui.store.home :as home]))

(defn find-product [store product-id]
  (let [product-id (c/parse-long product-id)]
    (some #(when (= (:db/id %) product-id) %)
          (:store/items store))))

(def compute-route-params #(select-keys % [:route-params]))
(def compute-store #(select-keys % [:store]))

(def route-map {:store-dashboard/order-list           {:component   ol/OrderList
                                                       :computed-fn compute-store}
                :store-dashboard/order-list-new       {:component   ol/OrderList
                                                       :computed-fn compute-store}
                :store-dashboard/order-list-fulfilled {:component   ol/OrderList
                                                       :computed-fn compute-store}
                :store-dashboard/order                {:component   oef/OrderEditForm
                                                       :computed-fn compute-route-params}
                :store-dashboard/create-order         {:component   oef/OrderEditForm
                                                       :computed-fn (fn [{:keys [store route-params]}]
                                                                      {:route-params route-params
                                                                       :products     (:store/items store)})}
                :store-dashboard/profile              {:component   es/EditStore
                                                       :computed-fn compute-store}
                :store-dashboard/policies             {:component   es/EditStore
                                                       :computed-fn compute-store}
                :store-dashboard/stream               {:component   ss/StreamSettings
                                                       :computed-fn compute-store}
                :store-dashboard/settings             {:component   as/AccountSettings
                                                       :computed-fn compute-store}
                :store-dashboard/product-list         {:component   pl/ProductList
                                                       :computed-fn compute-route-params}
                :store-dashboard/create-product       {:component   pef/ProductEditForm
                                                       :computed-fn (fn [{:keys [store route-params]}]
                                                                      {:route-params route-params
                                                                       :store        store})}
                :store-dashboard/product              {:component   pef/ProductEditForm
                                                       :computed-fn (fn [{:keys [store route-params]}]
                                                                      {:route-params route-params
                                                                       :store        store
                                                                       :product      (find-product store (:product-id route-params))})}
                :store-dashboard/shipping             {:component   shipping/StoreShipping
                                                       :computed-fn (fn [{:keys [route-params]}]
                                                                      {:route-params route-params})}
                :store-dashboard                      {:component   home/StoreHome
                                                       :computed-fn (fn [{:keys [route-params]}]
                                                                      {:route-params route-params})}})

;(defn str->json [s]
;  #?(:cljs (cljs.reader/read-string s)
;     :clj  (clojure.data.json/read-str s :key-fn keyword)))

(defn get-route-params [component]
  (get-in (om/props component) [:query/current-route :route-params]))

(defn parse-route [route]
  (let [ns (namespace route)
        subroute (name route)
        path (clojure.string/split subroute #"#")]
    (keyword ns (first path))))

(defn sub-navbar [component]
  (let [{:query/keys [current-route]} (om/props component)
        {:keys [route route-params]} current-route
        {:keys [component computed-fn factory]} (get route-map (parse-route route))
        store-id (:store-id route-params)
        nav-breakpoint :medium]
    (dom/div
      (->> {:id "store-navbar"}
           (css/add-class :navbar-container))
      (dom/nav
        (->> (css/add-class :navbar)
             (css/add-class :top-bar))
        (when (satisfies? store-common/IDashboardNavbarContent component)
          (store-common/render-subnav component current-route))
        ))))

(defui Dashboard
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/store [:db/id
                    :store/uuid
                    {:store/profile [:store.profile/description
                                     :store.profile/name
                                     :store.profile/tagline
                                     :store.profile/return-policy
                                     ;:store.profile/shipping-policy
                                     ;:store.profile/shipping-fee
                                     {:store.profile/cover [:photo/id]}
                                     {:store.profile/photo [:photo/path :photo/id]}]}
                    {:store/owners [{:store.owner/user [:user/email]}]}
                    :store/stripe
                    {:store/sections [:db/id :store.section/label]}
                    {:order/_store [:order/items]}
                    {:store/items [:store.item/name
                                   :store.item/description
                                   :store.item/price
                                   :store.item/index
                                   {:store.item/section [:db/id :store.section/label]}
                                   {:store.item/photos [{:store.item.photo/photo [:photo/path :photo/id]}
                                                        :store.item.photo/index]}
                                   {:store.item/skus [:db/id
                                                      :store.item.sku/inventory
                                                      :store.item.sku/variation]}]}
                    {:stream/_store [:stream/state]}]}
     :query/current-route
     :query/messages
     {:routing/store-dashboard (medley/map-vals (comp om/get-query :component) route-map)}
     {:query/stripe-account [:stripe/legal-entity
                             :stripe/verification
                             :stripe/details-submitted?
                             :stripe/charges-enabled?
                             :stripe/payouts-enabled?]}])

  Object
  (initLocalState [_]
    {:selected-tab :products
     :did-mount?   false})
  (componentDidMount [this]
    (let [{:keys [did-mount?]} (om/get-state this)]
      (when-not did-mount?
        (om/update-state! this assoc :did-mount? true))))
  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [store current-route stripe-account]
           :as         props} (om/props this)
          {:keys [route route-params]} current-route
          store-id (get-in current-route [:route-params :store-id])
          stream-state (or (-> store :stream/_store first :stream/state) :stream.state/offline)]

      (common/page-container
        {:navbar navbar
         :id     "sulo-store-dashboard"}
        ;(sub-navbar this)

        (let [{:keys [component computed-fn factory]} (get route-map (parse-route route))
              factory (or factory (om/factory component))
              routed-props (:routing/store-dashboard props)]
          (factory (om/computed routed-props (computed-fn (assoc props :store store :route-params route-params)))))))))

(def ->Dashboard (om/factory Dashboard))

(router/register-component :store-dashboard Dashboard)