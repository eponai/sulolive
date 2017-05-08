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
    [eponai.web.ui.store.common :as store-common :refer [edit-button cancel-button save-button]]
    [eponai.common.ui.utils :refer [two-decimal-price]]
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.format :as f]
    [eponai.web.ui.store.edit-store :as es]
    [medley.core :as medley]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.format.date :as date]
    [eponai.web.ui.photo :as p]))

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
                :store-dashboard/profile        {:component   es/EditStore
                                                 :computed-fn compute-store}
                :store-dashboard/policies       {:component   es/EditStore
                                                 :computed-fn compute-store}
                :store-dashboard/stream         {:component   ss/StreamSettings
                                                 :computed-fn compute-store}
                :store-dashboard/settings       {:component   as/AccountSettings
                                                 :computed-fn compute-store}
                :store-dashboard/product-list   {:component   pl/ProductList
                                                 :computed-fn compute-route-params}
                :store-dashboard/create-product {:component   pef/ProductEditForm
                                                 :computed-fn (fn [{:keys [store route-params]}]
                                                                {:route-params route-params
                                                                 :store        store})}
                :store-dashboard/product        {:component   pef/ProductEditForm
                                                 :computed-fn (fn [{:keys [store route-params]}]
                                                                {:route-params route-params
                                                                 :store        store
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
        {:keys [component computed-fn factory]} (get route-map (parse-route route))
        store-id (:store-id route-params)
        nav-breakpoint :medium]
    (dom/div
      (->> {:id "store-navbar"}
           (css/add-class :navbar-container))
      (dom/nav
        (->> (css/add-class :navbar)
             (css/add-class :top-bar))
        (menu/horizontal
          (css/add-class :top-bar-left)
          (menu/item-text
            (css/show-for :large)
            (if (= route :store-dashboard)
              (dom/span nil "Dashboard")
              (when (satisfies? store-common/IDashboardNavbarContent component)
                (dom/span nil (store-common/subnav-title component current-route))))))

        (when (satisfies? store-common/IDashboardNavbarContent component)
          (store-common/render-subnav component current-route))
        (menu/horizontal nil)))))

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

    (dom/p nil
           (dom/i {:classes ["fa fa-check fa-fw"]})
           (dom/a
             {:href (when-not done? href)}
             content))))

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

            (dom/h1 (css/show-for-sr) "Dashboard")
            (dom/div
              (css/add-class :section-title)
              (dom/h2 nil "Your store")
              (dom/a
                (->> {:href (routes/url :store-dashboard/profile {:store-id store-id})}
                     (css/button-hollow)
                     (css/add-class :secondary))
                (dom/span nil "Manage profile")
                (dom/i {:classes ["fa fa-chevron-right"]})))
            (callout/callout-small
              (css/add-class :section-info)
              (grid/row
                (->> (css/align :center)
                     (css/align :middle))

                (grid/column
                  (->> (grid/column-size {:small 6 :medium 4})
                       (css/text-align :center))
                  (dom/h3 nil (get-in store [:store/profile :store.profile/name]))
                  (p/store-photo store {:transformation :transformation/thumbnail}))

                (grid/column
                  nil
                  (grid/row
                    (grid/columns-in-row {:small 1 :medium 2})
                    (grid/column
                      (css/text-align :center)
                      (dom/h3 nil "Products")
                      (dom/p (css/add-class :stat) (count (:store/items store)))
                      (dom/a
                        (->> {:href (routes/url :store-dashboard/order-list {:store-id store-id})}
                             (css/button-hollow)
                             (css/add-class :secondary))
                        (dom/span nil "Manage products")
                        (dom/i {:classes ["fa fa-chevron-right"]})))
                    (grid/column
                      (css/text-align :center)
                      (dom/h3 nil "Orders")
                      (dom/p (css/add-class :stat) (count (:order/_store store)))
                      (dom/a
                        (->> {:href (routes/url :store-dashboard/product-list {:store-id store-id})}
                             (css/button-hollow)
                             (css/add-class :secondary))
                        (dom/span nil "Manage orders")
                        (dom/i {:classes ["fa fa-chevron-right"]})))))))

            (callout/callout
              nil
              (grid/row
                (grid/columns-in-row {:small 3})
                (grid/column
                  (css/text-align :center)
                  (dom/h3 nil "Balance")
                  (dom/p (css/add-class :stat) (two-decimal-price 0)))
                (grid/column
                  (css/text-align :center)
                  (dom/h3 nil "Customers")
                  (dom/p (css/add-class :stat) 0))
                (grid/column
                  (css/text-align :center)
                  (dom/h3 nil "Payments")
                  (dom/p (css/add-class :stat) 0))))

            (dom/div
              (css/add-class :section-title)
              (dom/h2 nil "Getting started"))

            (callout/callout
              nil
              (menu/vertical
                nil

                (check-list-item
                  (some? (:store.profile/description (:store/profile store)))
                  (routes/url :store-dashboard/settings {:store-id store-id})
                  (dom/span nil "Describe your store. People love to hear your story."))

                (check-list-item
                  (boolean (not-empty (:store/items store)))
                  (routes/url :store-dashboard/create-product {:store-id store-id})
                  (dom/span nil "Add your first product to attract those customers."))

                (check-list-item
                  false
                  (routes/url :store-dashboard/stream {:store-id store-id})
                  (dom/span nil "Setup your first stream and hangout with your customers whenever you feel like it."))

                (check-list-item
                  (:stripe/details-submitted? stripe-account)
                  (routes/url :store-dashboard/settings#activate {:store-id store-id})
                  (dom/span nil "Activate your account. You know that boring stuff needed to so you can accept payments."))))

            (dom/div
              (css/add-class :section-title)
              (dom/h2 nil "Notifications"))
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
                       "Once you've activated you'll immediately be able to use all features of SULO Live. Your account details are reviewed with Stripe to ensure they comply with our terms of service. If there is a problem, we'll get in touch right away to resolve it as quickly as possible.")))

            (dom/div
              (css/add-class :section-title)
              (dom/h1 nil (dom/small nil "Questions?")))
            (callout/callout
              nil
              (dom/p nil
                     (dom/span nil "We love to hear from you! If you want to give us feedback, report problems, or even just say hi, shoot us an email at ")
                     (dom/a {:href "mailto:hello@sulo.live"} "hello@sulo.live")
                     (dom/span nil " and Miriam, Diana or Petter will help you out.")))






            ;(grid/row
            ;  (->> (css/align :center)
            ;       (css/add-class :expanded)
            ;       (css/add-class :collapse)
            ;       (grid/columns-in-row {:small 1 :medium 2}))
            ;  (grid/column
            ;    nil
            ;    (store-info-element this))
            ;  (grid/column
            ;    nil
            ;
            ;    (if (:stripe/details-submitted? stripe-account)
            ;      (verification-status-element this)
            ;      (callout/callout
            ;        (->> (css/add-class :notification)
            ;             (css/add-class :action))
            ;        (grid/row
            ;          nil
            ;          (grid/column
            ;            (css/add-class :shrink)
            ;            (dom/i {:classes ["fa fa-info fa-fw"]}))
            ;          (grid/column
            ;            nil
            ;            (callout/header nil "Activate your account")))
            ;        (dom/p nil
            ;               (dom/span nil "Before ")
            ;               (dom/a {:href (routes/url :store-dashboard/settings#activate {:store-id store-id})} (dom/span nil "activating your account"))
            ;               (dom/span nil ", you can only use SULO Live in test mode. You can manage your store, but it'll not be visible to the public."))
            ;        (dom/p nil
            ;               "Once you've activated you'll immediately be able to use all features of SULO Live. Your account details are reviewed with Stripe to ensure they comply with our terms of service. If there is a problem, we'll get in touch right away to resolve it as quickly as possible.")))))
            ))))))

(def ->Dashboard (om/factory Dashboard))

(router/register-component :store-dashboard Dashboard)