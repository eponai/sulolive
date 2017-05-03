(ns eponai.common.ui.navbar
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.utils :as ui-utils]
    [eponai.common.ui.elements.css :as css]
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.client.auth :as auth]
    [taoensso.timbre :refer [debug error]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.icons :as icons]
    [eponai.client.routes :as routes]
    [clojure.string :as s]))

(def dropdown-elements
  {:dropdown/user       "sl-user-dropdown"
   :dropdown/bag        "sl-shopping-bag-dropdown"
   :dropdown/collection "sl-collection-dropdown"})

;(defn compute-item-price [items]
;  (reduce + (map :store.item/price items)))

;(defn cart-dropdown [component {:keys [cart/items cart/price]}]
;  (let [{:keys [dropdown-key]} (om/get-state component)]
;    (my-dom/div
;      (cond->> {:classes [:dropdown-pane :cart-dropdown]}
;               (= dropdown-key :dropdown/bag)
;               (css/add-class :is-open))
;      (menu/vertical
;        {:classes [::css/cart]}
;        (menu/item nil
;                   (menu/vertical
;                     (css/add-class :nested)
;                     (map (fn [i]
;                            (let [{:store.item/keys [price photos] p-name :store.item/name :as item} (:store.item/_skus i)]
;                              (menu/item-link
;                                {:href    (routes/url :product {:product-id (:db/id item)})
;                                 :classes [:cart-link]}
;                                (photo/square
;                                  {:src (:photo/path (first photos))})
;                                (dom/div #js {:className ""}
;                                  (dom/div #js {:className "content-item-title-section"}
;                                    (dom/p nil (dom/span #js {:className "name"} p-name)))
;                                  (dom/div #js {:className "content-item-subtitle-section"}
;                                    (dom/strong #js {:className "price"}
;                                                (ui-utils/two-decimal-price price)))))))
;                          (take 3 items))))
;        (menu/item nil (dom/div #js {:className "callout transparent"}
;                         (if (< 3 (count items))
;                           (dom/small nil (str "You have " (- (count items) 3) " more item(s) in your bag"))
;                           (dom/small nil (str "You have " (count items) " item(s) in your bag")))
;                         (dom/h5 nil "Total: " (dom/strong nil (ui-utils/two-decimal-price (compute-item-price (map #(get % :store.item/_skus) items)))))))
;        (menu/item (css/text-align :center)
;                   (dom/a #js {:className "button expanded gray"
;                               :href      (routes/url :shopping-bag nil)} "View My Bag"))))))

(defn category-dropdown [component]
  (let [{:keys [dropdown-key]} (om/get-state component)
        {:query/keys [navigation]} (om/props component)]
    (my-dom/div
      (cond->> {:classes [:dropdown-pane :collection-dropdown]}
               (= dropdown-key :dropdown/collection)
               (css/add-class :is-open))
      (menu/vertical
        {:classes [::css/categories]}
        (map
          (fn [{:category/keys [label href]}]
            (menu/item-link {:href href} (dom/span nil label)))
          navigation)))))

(defn user-dropdown [component user owned-store]
  (let [{:keys [dropdown-key]} (om/get-state component)]
    (my-dom/div
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
              (menu/item-link
                {:href (routes/url :store-dashboard {:store-id (:db/id owned-store)})}
                (get-in owned-store [:store/profile :store.profile/name]))))
          (menu/item
            (css/add-class :my-stores)
            (dom/label nil (dom/small nil "Manage store"))
            (menu/vertical
              (css/add-class :nested)
              (menu/item-link
                {:href (routes/url :sell)}
                (my-dom/small nil "Start a store")))))
        (when user
          (menu/item
            (css/add-class :user-info)
            (menu/vertical
              (css/add-class :nested)
              (dom/label nil (dom/small nil "Your Account"))
              (menu/item-link {:href (routes/url :user {:user-id (:db/id user)})}
                              (dom/small nil "Profile"))
              (menu/item-link {:href (routes/url :user/order-list {:user-id (:db/id user)})}
                              (dom/small nil "Purchases")))))
        (menu/item nil
                   (menu/vertical
                     (css/add-class :nested)
                     (menu/item-link {:href "/logout"}
                                     (dom/small nil "Sign out"))))))))

(defn navbar-content [& content]
  (apply dom/div #js {:className "navbar top-bar"}
         content))

(defn collection-links [component disabled?]
  (map
    (fn [{:category/keys [href name] :as a}]
      (let [opts (when (not disabled?)
                   {:href href})]
        (menu/item-link
          (->> opts
               (css/add-class :category)
               (css/show-for :large))
          (dom/span nil (s/capitalize name)))))
    (:query/navigation (om/props component))))

(defn live-link [& [on-click]]
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
      (my-dom/strong
        nil
        ;(css/show-for :medium)
        ;; Wrap in span for server and client to render the same html
        (dom/span nil "Live"))
      ;(my-dom/div
      ;  (css/hide-for :medium)
      ;  (dom/i #js {:className "fa fa-video-camera fa-fw"}))
      )))

(defn navbar-brand [& [href]]
  (menu/item-link {:href (or href "/")
                   :id   "navbar-brand"}
                  (dom/span nil "Sulo")
                  (dom/small nil "Preview")))

(defn coming-soon-navbar [component]
  (let [{:keys [coming-soon? right-menu on-live-click]} (om/get-computed component)]
    (navbar-content
      (dom/div #js {:className "top-bar-left"}
        (menu/horizontal
          nil
          (navbar-brand (routes/url :coming-soon))

          (live-link on-live-click)
          (menu/item-dropdown
            (->> {:dropdown (category-dropdown component)
                  :onClick  #(.open-dropdown component :dropdown/collection)}
                 (css/hide-for :large)
                 (css/add-class :category))
            (dom/span nil "Shop"))

          (collection-links component true)))

      (dom/div #js {:className "top-bar-right"}
        right-menu))))

(defn help-navbar [component]
  (let [{:query/keys [auth owned-store]} (om/props component)]
    (navbar-content
      (dom/div #js {:className "top-bar-left"}
        (menu/horizontal
          nil
          (navbar-brand)
          (live-link)
          (menu/item nil
                     (my-dom/input {:type        "text"
                                    :placeholder "Search on SULO Live Help..."}))))

      (dom/div #js {:className "top-bar-right"}
        (menu/horizontal nil
                         (if (some? auth)
                           (menu/item-dropdown
                             {:dropdown (user-dropdown component auth owned-store)
                              :classes  [:user-photo-item]
                              :href     "#"
                              :onClick  #(.open-dropdown component :dropdown/user)}
                             (photo/user-photo {:user auth}))
                           (my-dom/a
                             (->> {:onClick #(auth/show-lock (:shared/auth-lock (om/shared component)))}
                                  (css/button-hollow))
                             (dom/span nil "Sign in"))))))))

(defn sidebar-category [component href title]
  (menu/item
    (css/add-class :category)
    (my-dom/a {:onClick #(do (.close-sidebar component)
                             (routes/set-url! component href))}
              (my-dom/span nil title))))

(defn sidebar-highlight [component route route-params title]
  (menu/item
    (css/add-class :category)
    (my-dom/a
      (->> {:onClick #(do (.close-sidebar component)
                          (routes/set-url! component route route-params))}
           (css/add-class ::css/highlight ))
      (my-dom/span nil title))))

(defn sidebar-link [component route route-params title]
  (menu/item
    nil
    (my-dom/a {:onClick #(do (.close-sidebar component)
                             (routes/set-url! component route route-params))}
              (my-dom/span nil title))))

(defn sidebar [component]
  (let [{:query/keys [auth owned-store navigation]} (om/props component)]
    (my-dom/div
      (css/add-class :sidebar-container {:onClick #(.close-sidebar component)})
      (my-dom/div {:classes [:sidebar-overlay]})
      (my-dom/div
        {:id      "sulo-sidebar"
         :classes [:sidebar]}
        (menu/vertical
          nil
          (menu/item
            nil
            (my-dom/label nil "Explore")
            (menu/vertical
              nil
              (sidebar-highlight component :live nil "LIVE")))
          (menu/item
            nil (my-dom/label nil "Shop by category")
            (menu/vertical
              nil
              (map
                (fn [{:category/keys [name href]}]
                  (sidebar-category component href (s/capitalize name)))
                navigation)))
          (when (some? auth)
            (menu/item nil
                       (my-dom/label nil "Your account")
                       (menu/vertical
                         nil
                         (sidebar-link component :user {:user-id (:db/id auth)} "Profile")
                         (sidebar-link component :user/order-list {:user-id (:db/id auth)} "Purchases")))
            )
          (when (some? owned-store)
            (menu/item
              nil
              (my-dom/label nil (str "Manage " (get-in owned-store [:store/profile :store.profile/name])))
              (menu/vertical
                nil
                (sidebar-link component :store-dashboard {:store-id (:db/id owned-store)} "Dashboard")
                (sidebar-link component :store-dashboard/stream {:store-id (:db/id owned-store)} "Stream")
                (sidebar-link component :store-dashboard/product-list {:store-id (:db/id owned-store)} "Products")
                (sidebar-link component :store-dashboard/order-list {:store-id (:db/id owned-store)} "Orders")
                (sidebar-link component :store-dashboard/settings {:store-id (:db/id owned-store)} "Settings"))))

          (when (and (some? auth)
                     (nil? owned-store))
            (menu/item nil (my-dom/a
                             (->> {:href (routes/url :sell)}
                                  (css/button)) (my-dom/span nil "Start a store"))))
          (if (some? auth)
            (menu/item nil (my-dom/a
                             (->> {:href "/logout"}
                                  (css/button-hollow)) (my-dom/span nil "Sign out")))
            (menu/item nil (my-dom/a
                             (->> {:onClick #(auth/show-lock (:shared/auth-lock (om/shared component)))}
                                  (css/button)) (my-dom/span nil "Sign in")))))))))

(defn standard-navbar [component]
  (let [{:keys [did-mount?]} (om/get-state component)
        {:keys [coming-soon?]} (om/get-computed component)
        {:query/keys [cart auth owned-store]} (om/props component)]
    (navbar-content
      (dom/div #js {:className "top-bar-left"}
        (menu/horizontal
          nil
          (menu/item
            nil
            (my-dom/a
              (css/hide-for :large {:onClick #(.open-sidebar component)})
              (my-dom/i {:classes ["fa fa-bars fa-fw"]})))
          (navbar-brand)
          (live-link)

          ;(menu/item-dropdown
          ;  (->> {:dropdown (category-dropdown component)
          ;        :href     "#"
          ;        :onClick  #(.open-dropdown component :dropdown/collection)}
          ;       (css/hide-for :large)
          ;       (css/add-class :category))
          ;  (dom/span nil "Shop"))

          (collection-links component false)))
      (dom/div #js {:className "top-bar-right"}
        (menu/horizontal
          nil
          (menu/item nil
                     ;(my-dom/a
                     ;  (->> {:id "search-icon"}
                     ;       (css/hide-for :medium))
                     ;  (dom/i #js {:className "fa fa-search fa-fw"}))
                     (my-dom/div
                       (css/show-for :medium)
                       (dom/input #js {:type        "text"
                                       :placeholder "Search on SULO..."
                                       :onKeyDown   (fn [e]
                                                      #?(:cljs
                                                         (when (= 13 (.. e -keyCode))
                                                           (let [search-string (.. e -target -value)]
                                                             (set! js/window.location (str "/goods?search=" search-string))))))})))
          (if (some? auth)
            (menu/item-dropdown
              (->> {:dropdown (user-dropdown component auth owned-store)
                    :classes  [:user-photo-item]
                    :href     "#"
                    :onClick  #(.open-dropdown component :dropdown/user)}
                   (css/show-for :large))
              (photo/user-photo {:user auth}))
            (menu/item
              (css/show-for :large)
              (my-dom/a
                (->> {:onClick #(auth/show-lock (:shared/auth-lock (om/shared component)))}
                     (css/button-hollow))
                (dom/span nil "Sign in")))
            )
          (when (some? auth)
            (menu/item
              (->> (css/hide-for :large)
                   (css/add-class :user-photo-item))
              (my-dom/a
                {:href (routes/url :user {:user-id (:db/id auth)})}
                (photo/user-photo {:user auth}))))
          (menu/item
            nil
            (my-dom/a {:classes ["shopping-bag-icon"]
                       :href (routes/url :shopping-bag)}
                      (icons/shopping-bag)
                      (when (< 0 (count (:user.cart/items cart)))
                        (my-dom/span (css/add-class :badge) (count (:user.cart/items cart)))))))))))

(defui Navbar
  static om/IQuery
  (query [_]
    [{:query/cart [{:user.cart/items [{:store.item/_skus [:store.item/price
                                                     {:store.item/photos [:photo/path]}
                                                     :store.item/name
                                                     {:store/_items [{:store/profile [:store.profile/name]}]}]}]}]}
     {:query/auth [:db/id
                   :user/email
                   {:user/profile [{:user.profile/photo [:photo/path]}]}]}
     {:query/owned-store [:db/id
                          {:store/profile [:store.profile/name {:store.profile/photo [:photo/path]}]}
                          ;; to be able to query the store on the client side.
                          {:store/owners [{:store.owner/user [:db/id]}]}]}
     {:query/navigation [:category/name :category/label :category/path :category/href]}
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
               ;(om/update-state! this assoc :sidebar-open? true)
               )))

  (close-sidebar [this]
    #?(:cljs (let [body (first (utils/elements-by-class "page-container"))
                   {:keys [on-close-sidebar-fn]} (om/get-state this)]
               (utils/remove-class-to-element body "sidebar-open")
               (.removeEventListener js/document "click" on-close-sidebar-fn)
               ;(.removeEventListener js/document "touchstart" on-close-sidebar-fn)
               ;(om/update-state! this assoc :sidebar-open? false)
               )))
  (initLocalState [this]
    {:cart-open?    false
     :sidebar-open? false
     #?@(:cljs [:on-click-event-fn #(.close-dropdown this %)
                :on-close-sidebar-fn #(.close-sidebar this)])})
  (componentWillUnmount [this]
    #?(:cljs
       (let [{:keys [lock on-click-event-fn]} (om/get-state this)]
         (.removeEventListener js/document "click" on-click-event-fn))))

  (componentDidMount [this]
    ;#?(:cljs (do
    ;           (when js/Auth0LockPasswordless
    ;             (let [lock (new js/Auth0LockPasswordless "JMqCBngHgOcSYBwlVCG2htrKxQFldzDh" "sulo.auth0.com")]
    ;               (om/update-state! this assoc :lock lock)))
    ;           (om/update-state! this assoc :did-mount? true)))
    )

  (render [this]
    (let [
          {:query/keys [cart auth current-route]} (om/props this)
          {:keys [route route-params]} current-route
          {:keys [sidebar-open?]} (om/get-state this)
          #?@(:cljs [screen-width (.-innerWidth js/window)]
              :clj  [screen-width 0])
          sidebar-width (* 0.75 screen-width)]

      (dom/div nil
        (dom/header #js {:id "sulo-navbar"}
                    (dom/div #js {:className "navbar-container"}
                      (dom/div #js {:className "top-bar navbar"}
                        (cond
                          ;; When the user is going through the checkout flow, don't let them navigate anywhere else.
                          (= route :checkout)
                          (navbar-content
                            (dom/div #js {:className "top-bar-left"}
                              (menu/horizontal
                                nil
                                (navbar-brand)
                                ;(menu/item-link {:href "/"
                                ;                 :id   "navbar-brand"}
                                ;                (dom/span nil "Sulo"))
                                )))
                          (or (= route :coming-soon) (= route :sell-soon))
                          (coming-soon-navbar this)
                          (and (some? route) (= (namespace route) "help"))
                          (help-navbar this)
                          :else
                          (standard-navbar this)))))

        (sidebar this)))))
(def ->Navbar (om/factory Navbar))

(defn navbar [props]
  (->Navbar props))