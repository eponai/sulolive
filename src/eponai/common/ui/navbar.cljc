(ns eponai.common.ui.navbar
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.utils :as ui-utils]
    [eponai.common.ui.elements.css :as css]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.photo :as photo]
    [taoensso.timbre :refer [debug error]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.icons :as icons]
    [eponai.client.routes :as routes]
    [clojure.string :as s]))

(def dropdown-elements
  {:dropdown/user       "sl-user-dropdown"
   :dropdown/bag        "sl-shopping-bag-dropdown"
   :dropdown/collection "sl-collection-dropdown"})

(defn compute-item-price [items]
  (reduce + (map :store.item/price items)))

(defn cart-dropdown [component {:keys [cart/items cart/price]}]
  (let [{:keys [dropdown-key]} (om/get-state component)]
    (my-dom/div
      (cond->> {:classes [:dropdown-pane :cart-dropdown]}
               (= dropdown-key :dropdown/bag)
               (css/add-class :is-open))
      (menu/vertical
        {:classes [::css/cart]}
        (menu/item nil
                   (menu/vertical
                     (css/add-class :nested)
                     (map (fn [i]
                            (let [{:store.item/keys [price photos] p-name :store.item/name :as item} (:store.item/_skus i)]
                              (menu/item-link
                                {:href    (routes/url :product {:product-id (:db/id item)})
                                 :classes [:cart-link]}
                                (photo/square
                                  {:src (:photo/path (first photos))})
                                (dom/div #js {:className ""}
                                  (dom/div #js {:className "content-item-title-section"}
                                    (dom/p nil (dom/span #js {:className "name"} p-name)))
                                  (dom/div #js {:className "content-item-subtitle-section"}
                                    (dom/strong #js {:className "price"}
                                                (ui-utils/two-decimal-price price)))))))
                          (take 3 items))))
        (menu/item nil (dom/div #js {:className "callout transparent"}
                         (if (< 3 (count items))
                           (dom/small nil (str "You have " (- (count items) 3) " more item(s) in your bag"))
                           (dom/small nil (str "You have " (count items) " item(s) in your bag")))
                         (dom/h5 nil "Total: " (dom/strong nil (ui-utils/two-decimal-price (compute-item-price (map #(get % :store.item/_skus) items)))))))
        (menu/item (css/text-align :center)
                   (dom/a #js {:className "button expanded gray"
                               :href      (routes/url :shopping-bag nil)} "View My Bag"))))))

(defn category-dropdown [component]
  (let [{:keys [dropdown-key]} (om/get-state component)]
    (my-dom/div
      (cond->> {:classes [:dropdown-pane :collection-dropdown]}
               (= dropdown-key :dropdown/collection)
               (css/add-class :is-open))
      (menu/vertical
        {:classes [::css/categories]}
        (map-indexed
          (fn [i c]
            (menu/item-link {:href (routes/url :products/categories {:category c})}
                            (dom/span nil (s/capitalize c))))
          ["women" "men" "kids" "home" "art"])))))

(defn user-dropdown [component user]
  (let [store (get (first (get user :store.owner/_user)) :store/_owners)
        {:keys [dropdown-key]} (om/get-state component)]
    (my-dom/div
      (cond->> (->> (css/add-class :dropdown-pane)
                    (css/add-class :user-dropdown))
               (= dropdown-key :dropdown/user)
               (css/add-class :is-open))
      (menu/vertical
        (css/add-class :user-dropdown-menu)

        ;(when user
        ;  (menu/item
        ;    (css/add-class :user-info)
        ;    (dom/a #js {:href (routes/url :user/order-list {:user-id (:db/id user)})}
        ;           (dom/span nil "Purchases"))))
        ;(when user
        ;  (menu/item
        ;    (css/add-class :user-info)
        ;    (dom/a #js {:href (routes/url :user {:user-id (:db/id user)})}
        ;           (dom/span nil "Profile"))))
        (when store
          (menu/item
            (css/add-class :my-stores)
            (dom/label nil (dom/small nil "Manage Store"))
            (menu/vertical
              (css/add-class :nested)
              (menu/item-link
                {:href (routes/url :store-dashboard {:store-id (:db/id store)})}
                (:store/name store)))))
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

(defn collection-links [& [disabled?]]
  (map-indexed
    (fn [i c]
      (let [opts (cond-> {:key  (str "nav-" c "-" i)}
                         (not disabled?)
                         (assoc :href (routes/url :products/categories {:category (.toLowerCase c)})))]
        (menu/item-link
          (->> opts
               (css/add-class :category)
               (css/show-for :large))
          (dom/span nil (s/capitalize c)))))
    ["women" "men" "kids" "home" "art"]))

(defn live-link [& [on-click]]
  (let [opts (if on-click
                 {:key "nav-live"
                  :onClick on-click}
                 {:key "nav-live"
                  :href (routes/url :live)})]
    (menu/item-link
      (->> opts
        (css/add-class ::css/highlight)
        (css/add-class :navbar-live))
      (my-dom/strong
        (css/show-for :medium)
        ;; Wrap in span for server and client to render the same html
        (dom/span nil "Live"))
      (my-dom/div
        (css/hide-for :medium)
        (dom/i #js {:className "fa fa-video-camera fa-fw"})))))

(defn navbar-brand [& [href]]
  (menu/item-link {:href (or href "/")
                   :id   "navbar-brand"}
                  (dom/span nil "Sulo")))

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
                  :onClick #(.open-dropdown component :dropdown/collection)}
                 (css/hide-for :large)
                 (css/add-class :category))
            (dom/span nil "Shop"))

          (collection-links true)))

      (dom/div #js {:className "top-bar-right"}
        right-menu))))

(defn standard-navbar [component]
  (let [{:keys [did-mount?]} (om/get-state component)
        {:keys [coming-soon?]} (om/get-computed component)
        {:query/keys [cart auth]} (om/props component)]
    (navbar-content
      (dom/div #js {:className "top-bar-left"}
        (menu/horizontal
          nil
          (navbar-brand)
          (live-link)

          (menu/item-dropdown
            (->> {:dropdown (category-dropdown component)
                  :href "#"
                  :onClick #(.open-dropdown component :dropdown/collection)}
                 (css/hide-for :large)
                 (css/add-class :category))
            (dom/span nil "Shop"))

          (collection-links)))
      (dom/div #js {:className "top-bar-right"}
        (menu/horizontal
          nil
          (menu/item nil
                     (my-dom/a
                       (->> {:id "search-icon"}
                            (css/hide-for :medium))
                       (dom/i #js {:className "fa fa-search fa-fw"}))
                     (my-dom/div
                       (css/show-for :medium)
                       (dom/input #js {:type        "text"
                                       :placeholder "Search on SULO..."
                                       :onKeyDown   (fn [e]
                                                      #?(:cljs
                                                         (when (= 13 (.. e -keyCode))
                                                           (let [search-string (.. e -target -value)]
                                                             (set! js/window.location (str "/goods?search=" search-string))))))})))
          ;(when-let [store (get (first (get auth :store.owner/_user)) :store/_owners)]
          ;  (menu/item {:classes [:store-photo-item]}
          ;             (dom/a #js {:href (routes/url :store-dashboard {:store-id (:db/id store)})}
          ;                    (photo/store-photo store))))

          (menu/item-dropdown
            {:dropdown (user-dropdown component auth)
             :classes  [:user-photo-item]
             :href "#"
             :onClick  #(.open-dropdown component :dropdown/user)}
            (photo/user-photo auth))

          (if did-mount?
            (menu/item-dropdown
              {:dropdown (cart-dropdown component cart)
               :href (routes/url :shopping-bag)}
              (icons/shopping-bag))
            (menu/item-dropdown
              {:href (routes/url :shopping-bag)}
              (icons/shopping-bag))))))))

(defui Navbar
  static om/IQuery
  (query [_]
    [{:query/cart [{:cart/items [{:store.item/_skus [:store.item/price
                                                     {:store.item/photos [:photo/path]}
                                                     :store.item/name
                                                     {:store/_items [:store/name]}]}]}]}
     {:query/auth [:db/id
                   :user/email
                   {:user/photo [:photo/path]}
                   {:store.owner/_user [{:store/_owners [:store/name :db/id
                                                         {:store/photo [:photo/path]}]}]}]}
     '{:query/top-categories [:category/label :category/path :category/level {:category/children ...}]}
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
    #?(:cljs (let [{:keys [lock]} (om/get-state this)
                   current-url js/window.location.href
                   options (clj->js {:connections  ["facebook"]
                                     :callbackURL  (str js/window.location.origin "/auth")
                                     :authParams   {:scope            "openid email user_friends"
                                                    :connectionScopes {"facebook" ["email" "public_profile" "user_friends"]}
                                                    :state            current-url}
                                     :primaryColor "#9A4B4F"
                                     :dict         {:title "SULO"}
                                     :icon         ""
                                     ;:container "modal"
                                     })]
               (.socialOrMagiclink lock options))))

  (initLocalState [this]
    {:cart-open?   false
     #?@(:cljs [:on-click-event-fn #(.close-dropdown this %)])})
  (componentWillUnmount [this]
    #?(:cljs
       (let [{:keys [lock on-click-event-fn]} (om/get-state this)]
         (.removeEventListener js/document "click" on-click-event-fn))))

  (componentDidMount [this]
    #?(:cljs (do
               (when js/Auth0LockPasswordless
                 (let [lock (new js/Auth0LockPasswordless "JMqCBngHgOcSYBwlVCG2htrKxQFldzDh" "sulo.auth0.com")]
                   (om/update-state! this assoc :lock lock)))
               (om/update-state! this assoc :did-mount? true))))

  (render [this]
    (let [
          {:query/keys [cart auth current-route top-categories]} (om/props this)
          {:keys [route route-params]} current-route]

      (debug "Navbar categories: " top-categories)
      (debug "Route: " route)
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
                                  (menu/item-link {:href "/"
                                                   :id   "navbar-brand"}
                                                  (dom/span nil "Sulo")))))
                            (or (= route :coming-soon) (= route :sell-soon))
                            (coming-soon-navbar this)
                            :else
                            (standard-navbar this))))))))
(def ->Navbar (om/factory Navbar))

(defn navbar [props]
  (->Navbar props))