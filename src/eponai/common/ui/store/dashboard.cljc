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
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.format :as f]
    [eponai.common.ui.elements.photo :as photo]
    [medley.core :as medley]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.format.date :as date]))

(defn find-product [store product-id]
  (let [product-id (c/parse-long product-id)]
    (some #(when (= (:db/id %) product-id) %)
          (:store/items store))))

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
        store-id (:store-id route-params)
        nav-breakpoint :medium]
    (dom/div
      (->> {:id "store-navbar"}
           (css/add-class :navbar-container)
           (css/show-for :large))
      (dom/nav
        (->> (css/add-class :navbar)
             (css/add-class :top-bar))
        (menu/horizontal
          nil
          (menu/item (when (= route :store-dashboard)
                       (css/add-class ::css/is-active))
                     (dom/a
                       (css/add-class :category {:href (routes/url :store-dashboard {:store-id store-id})})
                       (dom/span (css/show-for nav-breakpoint) "Dashboard")
                       (dom/i
                         (css/hide-for nav-breakpoint {:classes [:fa :fa-dashboard :fa-fw]}))))
          (menu/item (when (= route :store-dashboard/stream)
                       (css/add-class ::css/is-active))
                     (dom/a
                       (css/add-class :category {:href (routes/url :store-dashboard/stream {:store-id store-id})})
                       (dom/span (css/show-for nav-breakpoint) "Stream")
                       (dom/i
                         (css/hide-for nav-breakpoint {:classes [:fa :fa-video-camera :fa-fw]}))))
          (menu/item
            (when (or (= route :store-dashboard/product-list)
                      (= route :store-dashboard/product)
                      (= route :store-dashboard/create-product))
              (css/add-class ::css/is-active))
            (dom/a
              (css/add-class :category {:href (routes/url :store-dashboard/product-list {:store-id store-id})})
              (dom/span (css/show-for nav-breakpoint) "Products")
              (dom/i
                (css/hide-for nav-breakpoint {:classes [:fa :fa-gift :fa-fw]}))))
          (menu/item
            (when (or (= route :store-dashboard/order-list)
                      (= route :store-dashboard/order))
              (css/add-class ::css/is-active))
            (dom/a
              (css/add-class :category {:href (routes/url :store-dashboard/order-list {:store-id store-id})})
              (dom/span (css/show-for nav-breakpoint) "Orders")
              (dom/i
                (css/hide-for nav-breakpoint {:classes [:fa :fa-file-text-o :fa-fw]}))))
          (menu/item
            (when (= (parse-route route) :store-dashboard/settings)
              (css/add-class ::css/is-active))
            (dom/a
              (css/add-class :category {:href (routes/url :store-dashboard/settings {:store-id store-id})})
              (dom/span (css/show-for nav-breakpoint) "Settings")
              (dom/i
                (css/hide-for nav-breakpoint {:classes [:fa :fa-video-camera :fa-fw]})))))))))

(defn store-info-element [component]
  (let [{:query/keys [store stripe-account current-route]} (om/props component)
        {:store/keys [profile]} store
        {store-name :store.profile/name} profile
        store-id (get-in current-route [:route-params :store-id])
        ;; Implement a :query/stream-by-store-id ?
        stream-state (or (-> store :stream/_store first :stream/state) :stream.state/offline)]

    (dom/div
      nil
      (callout/callout
        (css/add-class :profile-photo-container)
        (callout/header nil store-name)
        (grid/row
          (css/align :center)
          (grid/column
            (grid/column-size {:small 12 :medium 10 :large 8})
            (photo/store-photo store)))
        (grid/row
          (css/align :center)
          (grid/column
            (css/add-class :shrink)
            (dom/a
              (css/button-hollow {:href (routes/url :store {:store-id store-id})}) "View"))
          (grid/column
            (css/add-class :shrink)
            (dom/a
              (css/button-hollow {:href (routes/url :store-dashboard/settings {:store-id store-id})}) "Edit"))))
      (callout/callout
        (css/add-class :stream-status)
        (dom/a
          (->> (css/button-hollow {:href (routes/url :store-dashboard/stream {:store-id store-id})})
               (css/add-class :primary))
               (dom/span nil "Stream status")
               (dom/span
                 (cond->> (css/add-class :label)
                          (= stream-state :stream.state/offline)
                          (css/add-class :primary)
                          (= stream-state :stream.state/online)
                          (css/add-class :success)
                          (= stream-state :stream.state/live)
                          (css/add-class :highlight))
                 (name stream-state))))
      (callout/callout
        (css/add-class :stream-status)
        (let [disabled-reason (get-in stripe-account [:stripe/verification :stripe.verification/disabled-reason])
              status (if (some? disabled-reason) :alert :green)]
          (dom/a
            (->> (css/button-hollow {:href (when (= status :unverified)
                                             (routes/url :store-dashboard/settings#activate {:store-id store-id}))})
                 (css/add-class :primary))
            (dom/span nil "Account status")
            (if (some? status)
              (dom/span
                (->> (css/add-class :hollow (css/add-class :label))
                     (css/add-class status))
                (if (some? disabled-reason) "Disabled" "Enabled"))
              (dom/i {:classes ["fa fa-spinner fa-spin"]}))))))))

(defn verification-status-element [component]
  (let [{:query/keys [stripe-account current-route]} (om/props component)
        {:stripe/keys [charges-enabled? payouts-enabled? verification]} stripe-account
        {:stripe.verification/keys [due-by fields-needed disabled-reason]} verification
        store-id (get-in current-route [:route-params :store-id])
        is-alert? (or (false? charges-enabled?) (false? payouts-enabled?) (when (some? due-by) (< due-by (date/current-secs))))
        is-warning? (and (not-empty fields-needed) (some? due-by))

        disabled-labels {:fields_needed (dom/p nil (dom/span nil "More information is needed to verify your account. Please ")
                                               (dom/a nil (dom/span nil "provide the required information")) (dom/span nil " to re-enable the account."))}]
    (when (or is-alert? is-warning? (not-empty fields-needed))
      (callout/callout
        (cond->> (css/add-class :account-status (css/add-class :notification))
                 (or is-alert? is-warning?)
                 (css/add-class :warning))
        (grid/row
          nil
          (grid/column
            (css/add-class :shrink)
            (dom/i {:classes ["fa fa-warning fa-fw"]}))
          (grid/column
            nil
            (dom/p
              nil
              (cond (false? charges-enabled?)
                    (dom/strong nil "Charges from this account are disabled")
                    (false? payouts-enabled?)
                    (dom/strong nil "Payouts to this account are disabled")
                    (some? due-by)
                    (dom/strong nil "More information is needed to verify your account")
                    (not-empty fields-needed)
                    (dom/strong nil "More information may be needed to verify your account")))
            (cond (false? charges-enabled?)
                  (get disabled-labels (keyword disabled-reason))
                  (false? payouts-enabled?)
                  (get disabled-labels (keyword disabled-reason))
                  (some? due-by)
                  (dom/p nil
                         (dom/span nil "More information needs to be collected to keep this account enabled. Please ")
                         (dom/a {:href (routes/url :store-dashboard/settings#activate {:store-id store-id})} "provide the required information")
                         (dom/span nil " to prevent disruption in service to this account."))
                  (some? fields-needed)
                  (dom/p nil
                         (dom/span nil "If this account continues to process more volume, more information may need to be collected. To prevent disruption in service to this account you can choose to ")
                         (dom/a {:href (routes/url :store-dashboard/settings#activate {:store-id store-id})} "provide the information")
                         (dom/span nil " proactively.")))
            ))))))

(defn check-list-item [done? href & [content]]
  (menu/item
    (cond->> (css/add-class :getting-started-item)
             done?
             (css/add-class :done))
    (dom/i {:classes ["fa fa-check fa-fw"]})
    (dom/a
      (css/button-hollow {:href (when-not done? href)})
      content)))

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
                                     {:store.profile/photo [:photo/path]}]}
                    {:store/owners [{:store.owner/user [:user/email]}]}
                    :store/stripe
                    {:store/items [:store.item/name
                                   :store.item/description
                                   :store.item/price
                                   {:store.item/photos [{:store.item.photo/photo [:photo/path]}
                                                        :store.item.photo/index]}
                                   {:store.item/skus [:db/id
                                                      {:store.item.sku/inventory [:store.item.sku.inventory/value]}
                                                      :store.item.sku/variation]}]}
                    {:stream/_store [:stream/state]}]}
     :query/current-route
     :query/messages
     {:routing/store-dashboard (-> (medley/map-vals (comp om/get-query :component) route-map)
                                   (assoc :store-dashboard []))}
     {:query/stripe-account [:stripe/legal-entity
                             :stripe/verification
                             :stripe/details-submitted?
                             :stripe/charges-enabled?
                             :stripe/payouts-enabled?]}])
  Object
  (initLocalState [_]
    {:selected-tab :products
     :did-mount? false})
  (componentDidMount [this]
    (let [{:keys [did-mount?]} (om/get-state this)]
      (when-not did-mount?
        (om/update-state! this assoc :did-mount? true))))
  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [store current-route stripe-account]
           :as         props} (om/props this)
          {:keys [route route-params]} current-route
          store-id (get-in current-route [:route-params :store-id])]

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
          (dom/div
            {:id "sulo-main-dashboard"}
            (grid/row-column
              nil
              (common/wip-label this)
              (dom/h3 nil (dom/span nil "Dashboard ")))
            (grid/row
              (css/align :center)
              (grid/column
                (grid/column-size {:small 12 :medium 4 :large 3})
                (store-info-element this))
              (grid/column
                (grid/column-size {:small 12 :medium 8 :large 9})
                (callout/callout
                  nil
                  (callout/header nil "Getting started")
                  (menu/vertical
                    nil

                    (check-list-item
                      (some? (:store.profile/description (:store/profile store)))
                      (routes/url :store-dashboard/settings {:store-id store-id})
                      (dom/span nil "Describe your store"))

                    (check-list-item
                      (boolean (not-empty (:store/items store)))
                      (routes/url :store-dashboard/create-product {:store-id store-id})
                      (dom/span nil "Add your first product"))

                    (check-list-item
                      false
                      (routes/url :store-dashboard/stream {:store-id store-id})
                      (dom/span nil "Setup your first stream"))

                    (check-list-item
                      (:stripe/details-submitted? stripe-account)
                      (routes/url :store-dashboard/settings#activate {:store-id store-id})
                      (dom/span nil "Activate your account"))))

                (if (:stripe/details-submitted? stripe-account)
                  (verification-status-element this)
                  (callout/callout
                    (->> (css/add-class :notification)
                         (css/add-class :action))
                    (grid/row
                      nil
                      (grid/column
                        (css/add-class :shrink)
                        (dom/i {:classes ["fa fa-info fa-fw"]}))
                      (grid/column
                        nil
                        (callout/header nil "Activate your account")))
                    (dom/p nil
                           (dom/span nil "Before ")
                           (dom/a {:href (routes/url :store-dashboard/settings#activate {:store-id store-id})} (dom/span nil "activating your account"))
                           (dom/span nil ", you can only use SULO Live in test mode. You can manage your store, but it'll not be visible to the public."))
                    (dom/p nil
                           "Once you've activated you'll immediately be able to use all features of SULO Live. Your account details are reviewed with Stripe to ensure they comply with our terms of service. If there is a problem, we'll get in touch right away to resolve it as quickly as possible.")))))))))))

(def ->Dashboard (om/factory Dashboard))
