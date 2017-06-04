(ns eponai.common.ui.navbar
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.next :as om :refer [defui]]
    [eponai.common.shared :as shared]
    [eponai.client.auth :as auth]
    [taoensso.timbre :refer [debug error]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.icons :as icons]
    [eponai.client.routes :as routes]
    [clojure.string :as s]
    [eponai.web.ui.photo :as photo]
    [clojure.string :as string]
    [eponai.common.ui.router :as router]
    [eponai.web.social :as social]
    [eponai.web.ui.button :as button]
    [eponai.common.mixpanel :as mixpanel]))

(def dropdown-elements
  {:dropdown/user       "sl-user-dropdown"
   :dropdown/bag        "sl-shopping-bag-dropdown"
   :dropdown/collection "sl-collection-dropdown"})

(defn user-dropdown [component user owned-store]
  (let [{:keys [dropdown-key]} (om/get-state component)
        track-event (fn [k & [p]] (mixpanel/track-key k (merge p {:source "nav-dropdown"})))]
    (dom/div
      (cond->> (->> (css/add-class :dropdown-pane)
                    (css/add-class :user-dropdown))
               (= dropdown-key :dropdown/user)
               (css/add-class :is-open))
      (menu/vertical
        (css/add-class :user-dropdown-menu)
        (if owned-store
          (menu/item
            (css/add-class :my-stores)
            (dom/label nil (dom/small nil "Manage Store"))
            (menu/vertical
              (css/add-class :nested)
              (let [store-name (get-in owned-store [:store/profile :store.profile/name])]
                (menu/item-link
                  {:href    (routes/url :store-dashboard {:store-id (:db/id owned-store)})
                   :onClick #(track-event ::mixpanel/go-to-manage-store {:store-id   (:db/id owned-store)
                                                                         :store-name store-name})}
                  (dom/span nil store-name)))))
          (menu/item
            (css/add-class :my-stores)
            (dom/label nil (dom/small nil "Manage store"))
            (menu/vertical
              (css/add-class :nested)
              (menu/item-link
                {:href    (routes/url :sell)
                 :onClick #(track-event ::mixpanel/go-to-start-store)}
                (dom/small nil "Start a store")))))
        (when user
          (menu/item
            (css/add-class :user-info)
            (menu/vertical
              (css/add-class :nested)
              (dom/label nil (dom/small nil "Your account"))
              (menu/item-link {:href    (routes/url :user/order-list {:user-id (:db/id user)})
                               :onClick #(track-event ::mixpanel/go-to-purchases)}
                              (dom/small nil "Purchases"))
              (menu/item-link {:href    (routes/url :user-settings {:user-id (:db/id user)})
                               :onClick #(track-event ::mixpanel/go-to-settings)}
                              (dom/small nil "Settings")))))
        (menu/item nil
                   (menu/vertical
                     (css/add-class :nested)
                     (menu/item-link {:href    "/logout"
                                      :onClick #(track-event ::mixpanel/signout)}
                                     (dom/small nil "Sign out"))))))))

(defn navbar-content [opts & content]
  (dom/div
    (->> (css/add-class :navbar opts)
         (css/add-class :top-bar))
    content))

(defn collection-links [component disabled?]
  (map
    (fn [{:category/keys [href name path] :as a}]
      (let [opts (if (not disabled?)
                   {:href    href
                    :onClick #(mixpanel/track-key ::mixpanel/shop-by-category {:source   "navbar"
                                                                               :category path})})]
        (menu/item-link
          (->> opts
               (css/add-class :category)
               (css/show-for :large))
          (dom/span nil (s/capitalize name)))))
    (:query/navigation (om/props component))))

(defn live-link [& [on-click title]]
  (let [opts (if on-click
               {:key     "nav-live"
                :onClick on-click}
               {:key  "nav-live"
                :href (routes/url :live)})]
    (menu/item-link
      (->> opts
           (css/add-class ::css/highlight)
           (css/add-class :navbar-live)
           (css/show-for :large))
      (dom/strong
        nil
        ;(css/show-for :medium)
        ;; Wrap in span for server and client to render the same html
        (dom/span nil "Live"))
      ;(my-dom/div
      ;  (css/hide-for :medium)
      ;  (dom/i #js {:className "fa fa-video-camera fa-fw"}))
      )))

(defn navbar-brand [& [href title subtitle]]
  (menu/item-link
    {:href (or href "/")
     :id   "navbar-brand"}
    (or title
        (dom/span nil "Sulo"))
    (or subtitle
        (dom/small nil "Preview"))))

(defn coming-soon-navbar [component]
  (let [{:keys [right-menu on-live-click]} (om/get-computed component)]
    (navbar-content
      nil
      (dom/div
        {:classes ["top-bar-left"]}
        (menu/horizontal
          nil
          (menu/item
            nil
            (dom/a
              (css/hide-for :large {:onClick #(.open-sidebar component)})
              (dom/i {:classes ["fa fa-bars fa-fw"]})))
          (navbar-brand (routes/url :coming-soon))

          (live-link on-live-click)
          ;(menu/item-dropdown
          ;  (->> {:dropdown (category-dropdown component)
          ;        :onClick  #(.open-dropdown component :dropdown/collection)}
          ;       (css/hide-for :large)
          ;       (css/add-class :category))
          ;  (dom/span nil "Shop"))

          (collection-links component true)))

      (dom/div
        {:classes ["top-bar-right"]}
        right-menu))))

(defn help-navbar [component]
  (let [{:query/keys [auth owned-store]} (om/props component)]
    (navbar-content
      nil
      (dom/div
        {:classes ["top-bar-left"]}
        (menu/horizontal
          nil
          (navbar-brand)
          (live-link)
          (menu/item nil
                     (dom/input {:type        "text"
                                 :placeholder "Search on SULO Live Help..."}))))

      (dom/div
        {:classes ["top-bar-right"]}
        (menu/horizontal
          nil
          (if (some? auth)
            (menu/item-dropdown
              {:dropdown (user-dropdown component auth owned-store)
               :classes  [:user-photo-item]
               :href     "#"
               :onClick  #(.open-dropdown component :dropdown/user)}
              (photo/user-photo auth {:transformation :transformation/thumbnail-tiny}))
            (button/button
              (button/hollow {:onClick #(auth/show-lock (shared/by-key component :shared/auth-lock))})
              (dom/span nil "Sign in"))))))))

(def routes->titles
  {:store-dashboard                      "Dashboard"
   :store-dashboard/profile              "Store info"
   :store-dashboard/stream               "Live stream"
   :store-dashboard/product-list         "Products"
   :store-dashboard/product              "Products"
   :store-dashboard/create-product       "Products"
   :store-dashboard/order-list           "Orders"
   :store-dashboard/order-list-new       "Orders"
   :store-dashboard/order-list-fulfilled "Orders"
   :store-dashboard/order                "Order"
   :store-dashboard/shipping "Shipping"
   :store-dashboard/settings             "Business"
   :store-dashboard/settings#payments    "Business"
   :store-dashboard/settings#general     "Business"
   :store-dashboard/settings#shipping    "Business"
   :store-dashboard/settings#activate    "Business"
   :store-dashboard/settings#business    "Business"
   :store-dashboard/settings#payouts "Business"})

(defn user-menu-item [component]
  (let [{:query/keys [auth owned-store]} (om/props component)]
    [
     (if (some? auth)
       (menu/item-dropdown
         (->> {:dropdown (user-dropdown component auth owned-store)
               :classes  [:user-photo-item]
               :href     "#"
               :onClick  #(.open-dropdown component :dropdown/user)}
              (css/show-for :large))
         (photo/user-photo auth {:transformation :transformation/thumbnail-tiny}))
       (menu/item
         (css/show-for :large)
         (button/button
           (button/hollow {:onClick #(auth/show-lock (shared/by-key component :shared/auth-lock))})
           (dom/span nil "Sign in"))))
     ;(when (some? auth)
     ;  (menu/item
     ;    (->> (css/hide-for :large)
     ;         (css/add-class :user-photo-item))
     ;    (dom/a
     ;      {:href (routes/url :user {:user-id (:db/id auth)})}
     ;      (p/user-photo auth {:transformation :transformation/thumbnail-tiny}))))
     ]))

(defn manage-store-navbar [component]
  (let [{:query/keys [auth owned-store current-route]} (om/props component)
        {:keys [inline-sidebar-hidden?]} (om/get-state component)
        toggle-inline-sidebar (fn []
                                #?(:cljs
                                   (let [body (first (utils/elements-by-class "page-container"))]
                                     (if inline-sidebar-hidden?
                                       (utils/remove-class-to-element body "inline-sidebar-hidden")
                                       (utils/add-class-to-element body "inline-sidebar-hidden"))
                                     ;(.addEventListener js/document "touchstart" on-close-sidebar-fn)
                                     (om/update-state! component assoc :inline-sidebar-hidden? (not inline-sidebar-hidden?))
                                     )))]

    (navbar-content
      {:classes ["store-dashboard"]}
      (dom/div
        {:classes ["top-bar-left"]}
        (menu/horizontal
          nil
          (menu/item
            nil
            (dom/a
              (css/hide-for :large {:onClick #(.open-sidebar component)})
              (dom/i {:classes ["fa fa-bars fa-fw"]}))
            (dom/a
              (css/show-for :large {:onClick toggle-inline-sidebar})
              (dom/i {:classes ["fa fa-bars fa-fw"]})))
          (menu/item-link
            (css/show-for :large {:href (routes/url :store-dashboard (:route-params current-route))
                                  :id   "navbar-brand"})
            (dom/span nil "SULO")
            (dom/small nil "Store preview"))

          (menu/item-text nil (dom/span nil (get routes->titles (:route current-route))))))

      (dom/div
        (css/add-class :top-bar-right)
        (menu/horizontal
          nil
          (menu/item-link
            (->> {:href    (routes/url :store {:store-id (:db/id owned-store)})
                  :classes ["store-name"]})
            (dom/span nil (get-in owned-store [:store/profile :store.profile/name])))
          (user-menu-item component))))))

(defn sidebar-highlight [component route route-params title]
  (let [{:query/keys [current-route]} (om/props component)
        {:keys [on-live-click]} (om/get-computed component)]
    (menu/item
      (css/add-class :category)
      (dom/a
        (->> {:onClick #(if (or (= (:route current-route route) :coming-soon)
                                (= (:route current-route route) :coming-soon/sell))
                         (when on-live-click
                           (on-live-click))
                         (routes/set-url! component route route-params))}
             (css/add-class ::css/highlight))
        (dom/span nil title)))))

(defn standard-navbar [component]
  (let [{:query/keys [cart loading-bar]} (om/props component)]
    (navbar-content
      nil
      (dom/div
        {:classes ["top-bar-left"]}
        (menu/horizontal
          nil
          (menu/item
            nil
            (dom/a
              (css/hide-for :large {:onClick #(.open-sidebar component)})
              (dom/i {:classes ["fa fa-bars fa-fw"]})))
          (navbar-brand)
          (live-link)

          (collection-links component false)
          (when (:ui.singleton.loading-bar/show? loading-bar)
            ;; TODO: Do a pretty loading bar somwhere in this navbar.
            ;; (menu/item nil "Loading...")
            )))
      (dom/div
        {:classes ["top-bar-right"]}
        (menu/horizontal
          nil
          (menu/item nil
                     (dom/div
                       (css/show-for :medium)
                       (dom/input {:type        "text"
                                   :placeholder "Search on SULO..."
                                   :onKeyDown   (fn [e]
                                                  #?(:cljs
                                                     (when (= 13 (.. e -keyCode))
                                                       (let [search-string (.. e -target -value)]
                                                         (mixpanel/track "Search products" {:source        "navbar"
                                                                                            :search-string search-string})
                                                         (routes/set-url! component
                                                                          :browse/all-items
                                                                          nil
                                                                          {:search search-string})))))})))
          (user-menu-item component)
          (menu/item
            nil
            (dom/a {:classes ["shopping-bag-icon"]
                    :href    (routes/url :shopping-bag)}
                   (icons/shopping-bag)
                   (when (< 0 (count (:user.cart/items cart)))
                     (dom/span (css/add-class :badge) (count (:user.cart/items cart)))))))))))

(defui Navbar
  static om/IQuery
  (query [_]
    [{:query/cart [{:user.cart/items [{:store.item/_skus [:store.item/price
                                                          {:store.item/photos [:photo/path]}
                                                          :store.item/name
                                                          {:store/_items [{:store/profile [:store.profile/name]}]}]}]}]}
     {:query/auth [:db/id
                   :user/email
                   {:user/stripe [:stripe/id]}
                   {:user/profile [{:user.profile/photo [:photo/path :photo/id]}]}]}
     {:query/owned-store [:db/id
                          {:store/profile [:store.profile/name {:store.profile/photo [:photo/path]}]}
                          ;; to be able to query the store on the client side.
                          {:store/owners [{:store.owner/user [:db/id]}]}]}
     {:query/navigation [:category/name :category/label :category/path :category/href]}
     {:query/loading-bar [:ui.singleton.loading-bar/show?]}
     :query/current-route])
  Object
  #?(:cljs
     (open-dropdown
       [this dd-key]
       (let [{:keys [on-click-event-fn]} (om/get-state this)]
         (om/update-state! this assoc :dropdown-key dd-key)
         (.addEventListener js/document "click" on-click-event-fn))))

  #?(:cljs
     (close-dropdown
       [this event]
       (let [{:keys [dropdown-key on-click-event-fn]} (om/get-state this)
             id (get dropdown-elements dropdown-key)]
         (debug "Clicked: " event)
         (when-not (= (.-id (.-target event)) id)
           (om/update-state! this dissoc :dropdown-key)
           (.removeEventListener js/document "click" on-click-event-fn)))))

  (open-signin [this]
    (debug "Open signin")
    ;#?(:cljs (let [{:keys [lock]} (om/get-state this)
    ;               current-url js/window.location.href
    ;               options (clj->js {:connections  ["facebook"]
    ;                                 :callbackURL  (str js/window.location.origin "/auth")
    ;                                 :authParams   {:scope            "openid email user_friends"
    ;                                                :connectionScopes {"facebook" ["email" "public_profile" "user_friends"]}
    ;                                                :state            current-url}
    ;                                 :primaryColor "#9A4B4F"
    ;                                 :dict         {:title "SULO"}
    ;                                 :icon         ""
    ;                                 ;:container "modal"
    ;                                 })]
    ;           (.socialOrMagiclink lock options)))
    )

  (open-sidebar [this]
    #?(:cljs (let [body (first (utils/elements-by-class "page-container"))
                   {:keys [on-close-sidebar-fn]} (om/get-state this)]
               (utils/add-class-to-element body "sidebar-open")
               (.addEventListener js/document "click" on-close-sidebar-fn)
               ;(.addEventListener js/document "touchstart" on-close-sidebar-fn)
               (om/update-state! this assoc :sidebar-open? true)
               )))

  (close-sidebar [this]
    #?(:cljs (let [body (first (utils/elements-by-class "page-container"))
                   {:keys [on-close-sidebar-fn]} (om/get-state this)]
               (utils/remove-class-to-element body "sidebar-open")
               (.removeEventListener js/document "click" on-close-sidebar-fn)
               ;(.removeEventListener js/document "touchstart" on-close-sidebar-fn)
               (om/update-state! this assoc :sidebar-open? false)
               )))
  #?(:cljs
     (shouldComponentUpdate [this props state]
                            (router/should-update-when-route-is-loaded this props state)))
  (initLocalState [this]
    {:cart-open?             false
     :sidebar-open?          false
     :inline-sidebar-hidden? false
     #?@(:cljs [:on-click-event-fn #(.close-dropdown this %)
                :on-close-sidebar-fn #(.close-sidebar this)
                :on-transition-iteration-fn (fn []
                                              (let [{:query/keys [loading-bar]} (om/props this)
                                                    is-loading? (:ui.singleton.loading-bar/show? loading-bar)]
                                                (when-let [spinner (utils/element-by-id "sl-global-spinner")]
                                                  (when-not is-loading?
                                                    (utils/remove-class-to-element spinner "is-active")))
                                                ))])})
  (componentWillUnmount [this]
    #?(:cljs
       (let [{:keys [lock on-click-event-fn on-transition-iteration-fn]} (om/get-state this)
             spinner (utils/element-by-id "sl-global-spinner")]
         (.removeEventListener js/document "click" on-click-event-fn)
         (when spinner
           (.removeEventListener spinner "webkitAnimationIteration" on-transition-iteration-fn)
           (.removeEventListener spinner "animationiteration" on-transition-iteration-fn)))))

  (componentWillReceiveProps [this next-props]
    (let [{:query/keys [loading-bar]} next-props]
      #?(:cljs
         (when-let [spinner (utils/element-by-id "sl-global-spinner")]
           (let [is-loading? (:ui.singleton.loading-bar/show? loading-bar)
                 spinner-active? (string/includes? (.-className spinner) "is-active")]
             (if is-loading?
               (when-not spinner-active?
                 (debug "ADD LOADER ACTIVE")
                 (utils/add-class-to-element spinner "is-active"))
               (when spinner-active?
                 (debug "REMOVE LOADER ACTIVE")
                 (utils/remove-class-to-element spinner "is-active"))))))))


  (componentDidMount [this]
    ;#?(:cljs (do
    ;           (when js/Auth0LockPasswordless
    ;             (let [lock (new js/Auth0LockPasswordless "JMqCBngHgOcSYBwlVCG2htrKxQFldzDh" "sulo.auth0.com")]
    ;               (om/update-state! this assoc :lock lock)))
    ;           (om/update-state! this assoc :did-mount? true)))
    #?(:cljs
       (let [{:keys [on-transition-iteration-fn]} (om/get-state this)
             spinner (utils/element-by-id "sl-global-spinner")]
         (when spinner
           (.addEventListener spinner "webkitAnimationIteration" on-transition-iteration-fn)
           (.addEventListener spinner "animationiteration" on-transition-iteration-fn)))
       )
    )

  (render [this]
    (let [
          {:query/keys [current-route loading-bar]} (om/props this)
          {:keys [route]} current-route
          {:keys [loading-spinner]} (om/get-state this)]

      (dom/div
        nil
        (dom/header
          {:id "sulo-navbar"}
          (dom/div
            {:classes ["navbar-container"]}
            (dom/div
              {:classes ["top-bar navbar"]}
              (cond
                ;; When the user is going through the checkout flow, don't let them navigate anywhere else.
                (= route :checkout)
                (navbar-content
                  nil
                  (dom/div
                    {:classes ["top-bar-left"]}
                    (menu/horizontal
                      nil
                      (navbar-brand))))
                (and (some? route)
                     (or (= route :store-dashboard) (= (name :store-dashboard) (namespace route))))
                (manage-store-navbar this)

                (or (= route :coming-soon) (= route :coming-soon/sell))
                (coming-soon-navbar this)

                (and (some? route) (= (namespace route) "help"))
                (help-navbar this)

                :else
                (standard-navbar this)))))
        ;(let [is-loading? (:ui.singleton.loading-bar/show? loading-bar)])
        #?(:cljs
           (dom/div
             (css/add-class :sl-global-spinner {:id "sl-global-spinner"}))
           :clj
           (dom/div
             (css/add-classes [:sl-global-spinner :is-active] {:id "sl-global-spinner"})))))))
(def ->Navbar (om/factory Navbar))

(defn navbar [props]
  (->Navbar props))



(defui Sidebar
  static om/IQuery
  (query [_]
    (om/get-query Navbar)
    ;[{:query/auth [:db/id]}
    ; {:query/owned-store [:db/id
    ;                      {:store/profile [:store.profile/name {:store.profile/photo [:photo/path]}]}
    ;                      ;; to be able to query the store on the client side.
    ;                      {:store/owners [{:store.owner/user [:db/id]}]}]}
    ; {:query/navigation [:category/name :category/label :category/path :category/href]}
    ; :query/current-route]
    )
  Object
  (render [this]
    (let [{:query/keys [auth owned-store navigation current-route]} (om/props this)
          {:keys [route]} current-route
          track-event (fn [k & [p]] (mixpanel/track-key k (merge p {:source "sidebar"})))]
      (dom/div
        (css/add-class :sidebar-container)
        (dom/div {:classes [:sidebar-overlay]})
        (dom/div
          {:id      "sulo-sidebar"
           :classes [:sidebar]}
          (if (and (some? route)
                   (or (= route :store-dashboard)
                       (= (namespace route) (name :store-dashboard))))
            ;; Store owner side menu
            [
             (menu/vertical
               nil
               (menu/item
                 nil
                 (menu/vertical
                   nil
                   (menu/item
                     (css/add-class :back)
                     (dom/a {:href    (routes/url :index nil)
                             :onClick #(mixpanel/track "Store: Go back to marketplace")}
                            (dom/i {:classes ["fa fa-chevron-left fa-fw"]})
                            (dom/span nil "SULO Live")))))
               (when (some? owned-store)
                 [
                  (menu/item
                    (when (= :store-dashboard (:route current-route))
                      (css/add-class :is-active))
                    (dom/a {:href    (routes/url :store-dashboard {:store-id (:db/id owned-store)})
                            :onClick #(track-event ::mixpanel/go-to-dashboard)}
                           (dom/div {:classes ["icon icon-home"]})
                           (dom/span nil "Home")))
                  (menu/item
                    (when (= :store-dashboard/stream (:route current-route))
                      (css/add-class :is-active))
                    (dom/a {:href    (routes/url :store-dashboard/stream {:store-id (:db/id owned-store)})
                            :onClick #(track-event ::mixpanel/go-to-stream-settings)}
                           (dom/div {:classes ["icon icon-stream"]})
                           (dom/span nil "Live stream")))
                  (menu/item
                    (when (= :store-dashboard/profile (:route current-route))
                      (css/add-class :is-active))
                    (dom/a {:href    (routes/url :store-dashboard/profile {:store-id (:db/id owned-store)})
                            :onClick #(track-event ::mixpanel/go-to-store-info)}
                           (dom/div {:classes ["icon icon-shop"]})
                           (dom/span nil "Store info")))
                  (menu/item
                    (when (contains? #{:store-dashboard/product-list
                                       :store-dashboard/create-product
                                       :store-dashboard/product} (:route current-route))
                      (css/add-class :is-active))
                    (dom/a {:href    (routes/url :store-dashboard/product-list {:store-id (:db/id owned-store)})
                            :onClick #(track-event ::mixpanel/go-to-products)}
                           (dom/div {:classes ["icon icon-product"]})
                           (dom/span nil "Products")))
                  (menu/item
                    (when (contains? #{:store-dashboard/order-list
                                       :store-dashboard/order-list-new
                                       :store-dashboard/order-list-fulfilled
                                       :store-dashboard/order} (:route current-route))
                      (css/add-class :is-active))
                    (dom/a {:href    (routes/url :store-dashboard/order-list {:store-id (:db/id owned-store)})
                            :onClick #(track-event ::mixpanel/go-to-orders)}
                           (dom/div {:classes ["icon icon-order"]})
                           (dom/span nil "Orders")))
                  (menu/item
                    (when (contains? #{:store-dashboard/shipping} (:route current-route))
                      (css/add-class :is-active))
                    (dom/a {:href    (routes/url :store-dashboard/shipping {:store-id (:db/id owned-store)})
                            ;:onClick #(track-event ::mixpanel/go-to-orders)
                            }
                           (dom/div {:classes ["icon icon-truck"]})
                           (dom/span nil "Shipping")))
                  (menu/item
                    (when (or (= :store-dashboard/settings#payouts (:route current-route))
                              (= :store-dashboard/settings#business (:route current-route)))
                      (css/add-class :is-active))
                    (dom/a {:href    (routes/url :store-dashboard/settings#payouts {:store-id (:db/id owned-store)})
                            :onClick #(track-event ::mixpanel/go-to-business)}
                           (dom/div {:classes ["icon icon-business"]})
                           (dom/span nil "Business")))]))
             (menu/vertical
               (css/add-class :footer-menu)
               (menu/item
                 (css/hide-for :large)
                 (menu/vertical (css/add-class :signout-menu)
                                (if (some? auth)
                                  (menu/item nil (dom/a {:href    (routes/url :logout)
                                                         :onClick #(track-event ::mixpanel/signout)} (dom/small nil "Sign out")))
                                  (menu/item nil (dom/a (css/button {:onClick #(do
                                                                                (track-event ::mixpanel/open-signin)
                                                                                (auth/show-lock (shared/by-key this :shared/auth-lock)))}) (dom/span nil "Sign in"))))))
               (menu/item
                 nil
                 (menu/horizontal
                   {:key "social"}
                   (menu/item nil (social/sulo-social-link :social/facebook))
                   (menu/item nil (social/sulo-social-link :social/instagram))))
               (menu/item-text nil (social/sulo-icon-attribution))
               (menu/item-text nil (social/sulo-copyright)))]


            ;; Consumer side menu.
            [(menu/vertical
               nil
               (menu/item
                 nil
                 (dom/label nil "Explore")
                 (menu/vertical
                   nil
                   (sidebar-highlight this :live nil "LIVE")))
               (menu/item
                 nil (dom/label nil "Shop by category")
                 (menu/vertical
                   nil
                   (map
                     (fn [{:category/keys [name path href]}]
                       (menu/item
                         (css/add-class :category)
                         (dom/a {:href    href
                                 :onClick #(mixpanel/track-key ::mixpanel/shop-by-category {:source   "sidebar"
                                                                                            :category path})}
                                (dom/span nil (s/capitalize name))))
                       ;(sidebar-category this href (s/capitalize name))
                       )
                     navigation)))
               (when (some? owned-store)
                 (menu/item
                   nil
                   (dom/label nil "Manage store")
                   (menu/vertical
                     nil
                     (let [store-name (get-in owned-store [:store/profile :store.profile/name])]
                       (menu/item
                         nil
                         (dom/a {:href    (routes/url :store-dashboard {:store-id (:db/id owned-store)})
                                 :onClick #(track-event ::mixpanel/go-to-manage-store {:store-id   (:db/id owned-store)
                                                                                       :store-name store-name})}
                                (dom/div {:classes ["icon icon-shop"]})
                                (dom/span nil store-name)))))))
               (when (some? auth)
                 (menu/item nil
                            (dom/label nil "Your account")
                            (menu/vertical
                              nil
                              ;(sidebar-link this :user {:user-id (:db/id auth)}
                              ;              (dom/div {:classes ["icon icon-profile"]})
                              ;              (dom/span nil "Profile"))
                              (menu/item
                                (when (= route (:route current-route))
                                  (css/add-class :is-active))
                                (dom/a {:href    (routes/url :user/order-list {:user-id (:db/id auth)})
                                        :onClick #(track-event ::mixpanel/go-to-purchases)}
                                       (dom/div {:classes ["icon icon-order"]})
                                       (dom/span nil "Purchases")))
                              (menu/item
                                (when (= route (:route current-route))
                                  (css/add-class :is-active))
                                (dom/a {:href    (routes/url :user/order-list {:user-id (:db/id auth)})
                                        :onClick #(track-event ::mixpanel/go-to-settings)}
                                       (dom/div {:classes ["icon icon-settings"]})
                                       (dom/span nil "Settings"))))))
               ;(when (and (some? auth)
               ;           (nil? owned-store))
               ;  (menu/item nil (dom/a
               ;                   (->> {:href (routes/url :sell)}
               ;                        (css/button)) (dom/span nil "Start a store"))))
               ;(when (nil? auth)
               ;  ;(menu/item nil (dom/a
               ;  ;                 (->> {:href "/logout"}
               ;  ;                      (css/button-hollow)) (dom/span nil "Sign out")))
               ;  (menu/item nil (dom/a
               ;                   (->> {:onClick #(auth/show-lock (shared/by-key this :shared/auth-lock))}
               ;                        (css/button)) (dom/span nil "Sign in"))))
               )
             (menu/vertical
               (css/add-class :footer-menu)
               (menu/item
                 (css/hide-for :large)
                 (menu/vertical (css/add-class :signout-menu)
                                (when (and (some? auth)
                                           (nil? owned-store))
                                  (menu/item nil (dom/a
                                                   (->> {:href    (routes/url :sell)
                                                         :onClick #(track-event ::mixpanel/go-to-start-store)}
                                                        (css/button)) (dom/span nil "Start a store"))))
                                (if (some? auth)
                                  (menu/item nil (dom/a {:href    "/logout"
                                                         :onClick #(track-event ::mixpanel/signout)}
                                                        (dom/small nil "Sign out")))
                                  (menu/item nil (dom/a
                                                   (->> {:onClick #(do
                                                                    (track-event ::mixpanel/open-signin)
                                                                    (auth/show-lock (shared/by-key this :shared/auth-lock)))}
                                                        (css/button)) (dom/span nil "Sign in")))
                                  ;(menu/item nil (dom/a (css/button {:onClick #(auth/show-lock (shared/by-key this :shared/auth-lock))})
                                  ;                      (dom/span nil "Sign in")))
                                  )))
               ;(menu/item
               ;  nil
               ;  (menu/horizontal
               ;    {:key "social"}
               ;    (menu/item-link {:href   "https://www.facebook.com/live.sulo"
               ;                     :target "_blank"}
               ;                    (dom/span {:classes ["icon icon-instagram"]}))
               ;    ;(menu/item-link nil (dom/i {:classes ["fa fa-twitter fa-fw"]}))
               ;    (menu/item-link {:href   "https://www.instagram.com/sulolive"
               ;                     :target "_blank"}
               ;                    (dom/span {:classes ["icon icon-facebook"]}))))
               ;<a href="https://icons8.com">Icon pack by Icons8</a>
               ;(menu/item-text nil (dom/a {:href   "https://icons8.com"
               ;                            :target "_blank"} (dom/small {:classes ["copyright"]} "Icons by Icons8")))
               (menu/item-text nil (dom/small {:classes ["copyright"]} "© eponai hb 2017")))]
            ))))))
(def ->Sidebar (om/factory Sidebar))