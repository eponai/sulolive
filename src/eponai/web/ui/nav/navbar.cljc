(ns eponai.web.ui.nav.navbar
  (:require
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.client.routes :as routes]
    [eponai.client.utils :as utils]
    [eponai.web.ui.photo :as photo]
    [eponai.common.shared :as shared]
    [eponai.client.auth :as auth]
    #?(:cljs
       [eponai.web.utils :as web.utils])
    [eponai.common.mixpanel :as mixpanel]
    [eponai.web.ui.nav.loading-bar :as loading]
    [eponai.common.ui.icons :as icons]
    [eponai.web.ui.nav.common :as nav.common]
    [eponai.web.ui.login :as login]
    [eponai.web.ui.notifications :as note]))

(def dropdown-elements
  {:dropdown/user       "sl-user-dropdown"
   :dropdown/bag        "sl-shopping-bag-dropdown"
   :dropdown/collection "sl-collection-dropdown"})

(defn navbar-content [opts & content]
  (dom/div
    (->> (css/add-class :navbar opts)
         (css/add-class :top-bar))
    content))


(defn navbar-brand [& [href title]]
  (menu/item-link
    {:href (or href "/")
     :id   "navbar-brand"}
    (or title
        (dom/span nil "Sulo"))))

(defn user-dropdown [component user owned-store]
  (let [{:keys [dropdown-key]} (om/get-state component)
        {:query/keys [locations]} (om/props component)
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
            (css/add-class :parent)
            (dom/label nil (dom/small nil "Manage Store"))
            (menu/vertical
              (css/add-class :nested)
              (let [store-name (get-in owned-store [:store/profile :store.profile/name])]
                (menu/item-link
                  {:href    (routes/store-url owned-store :store-dashboard)
                   :onClick #(do (track-event ::mixpanel/go-to-manage-store {:store-id   (:db/id owned-store)
                                                                             :store-name store-name})
                                 ;#?(:cljs (when (empty? locations)
                                 ;           (utils/set-locality (:db/id (:store/locality owned-store)))))
                                 )}
                  (dom/span nil store-name)))))
          (menu/item
            (css/add-class :parent)
            (dom/label nil (dom/small nil "Manage store"))
            (menu/vertical
              (css/add-class :nested)
              (menu/item-link
                {:href    (routes/url :sell)
                 :onClick #(track-event ::mixpanel/go-to-start-store)}
                (dom/small nil "Start a store")))))
        (when user
          (menu/item
            (css/add-class :parent)
            (menu/vertical
              (css/add-class :nested)
              (dom/label nil (dom/small nil "Your account"))
              (menu/item-link {:href    (routes/url :user/order-list {:user-id (:db/id user)})
                               :onClick #(track-event ::mixpanel/go-to-purchases)}
                              (dom/small nil "Purchases"))
              (menu/item-link {:href    (routes/url :user-settings {:user-id (:db/id user)})
                               :onClick #(track-event ::mixpanel/go-to-settings)}
                              (dom/small nil "Settings"))
              (menu/item-link {:href    (routes/url :landing-page)
                               :onClick #(track-event ::mixpanel/change-location)}
                              (dom/small nil "Change location")))))
        (menu/item nil
                   (menu/vertical
                     (css/add-class :nested)
                     (menu/item-link {:href    (routes/url :logout)
                                      :onClick #(track-event ::mixpanel/signout)}
                                     (dom/small nil "Sign out"))))))))

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
         (dom/strong nil (dom/small nil (:user.profile/name (:user/profile auth))))
         (photo/user-photo auth {:transformation :transformation/thumbnail-tiny}))
       (menu/item
         nil
         (dom/a
           {:onClick #(do
                       (debug "Click login: " (shared/by-key component :shared/login))
                       (auth/show-login (shared/by-key component :shared/login)))}
           (dom/strong nil (dom/small nil "Sign up / Sign in")))))]))

(defn help-navbar [component]
  (let [{:query/keys [auth owned-store]} (om/props component)]
    (navbar-content
      nil
      (dom/div
        {:classes ["top-bar-left"]}
        (menu/horizontal
          nil
          (navbar-brand)
          (nav.common/live-link component "navbar")
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
            (dom/a
              {:onClick #(auth/show-login (shared/by-key component :shared/login))}
              (dom/span nil "Sign up / Sign in"))))))))

(defn manage-store-navbar [component]
  (let [{:proxy/keys [notification]
         :query/keys [auth owned-store current-route locations]} (om/props component)
        {:keys [inline-sidebar-hidden?]} (om/get-state component)
        toggle-inline-sidebar (fn []
                                #?(:cljs
                                   (let [body (first (web.utils/elements-by-class "page-container"))]
                                     (if inline-sidebar-hidden?
                                       (web.utils/remove-class-to-element body "inline-sidebar-hidden")
                                       (web.utils/add-class-to-element body "inline-sidebar-hidden"))
                                     (om/update-state! component assoc :inline-sidebar-hidden? (not inline-sidebar-hidden?)))))]
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
            (dom/span nil "SULO"))

          (menu/item
            (css/show-for :medium)
            (dom/a {:href    (if locations
                               (routes/url :index {:locality (:sulo-locality/path locations)})
                               (routes/url :landing-page))
                    :onClick #(mixpanel/track "Store: Go back to marketplace" {:source "navbar"})}
                   (dom/strong nil (dom/small nil "Back to marketplace"))))

          (note/->Notifications (om/computed notification {:href (routes/store-url owned-store :store (:route-params current-route))
                                                           :type :notification.type/chat}))))

      (dom/div
        (css/add-class :top-bar-right)
        (menu/horizontal
          nil

          (menu/item-link
            (->> {:href    (routes/store-url owned-store :store (:route-params current-route))
                  :classes ["store-name"]})
            (dom/span nil (get-in owned-store [:store/profile :store.profile/name])))
          (user-menu-item component))))))

(defn standard-navbar [component]
  (let [{:proxy/keys [notification]
         :query/keys [owned-store cart loading-bar current-route locations auth]} (om/props component)]

    [(navbar-content
       nil
       (dom/div
         {:classes ["top-bar-left"]}
         (menu/horizontal
           nil
           (when (some? auth)
             (menu/item
               nil
               (dom/a
                 (css/hide-for :large {:onClick #(.open-sidebar component)})
                 (dom/i {:classes ["fa fa-bars fa-fw"]}))))
           (navbar-brand (if (not-empty locations)
                           (routes/url :index {:locality (:sulo-locality/path locations)})
                           (routes/url :landing-page)))
           (nav.common/live-link component "navbar")

           (nav.common/collection-links component "navbar")))

       (dom/div
         {:classes ["top-bar-right"]}
         (menu/horizontal
           nil

           (when (some? auth)
             (note/->Notifications (om/computed notification {:type :notification.type/notification})))
           (when (some? owned-store)
             (note/->Notifications (om/computed notification {:type :notification.type/chat
                                                              :href (routes/store-url owned-store :store)})))
           (when (nil? auth)
             (menu/item
               nil
               (dom/a {:href (routes/url :about)}
                      (dom/strong nil (dom/small nil "About us")))))
           (user-menu-item component)
           (menu/item
             (css/add-class :shopping-bag)
             (dom/a {:classes ["shopping-bag-icon"]
                     :href    (routes/url :shopping-bag)}
                    ;(dom/span (css/add-class ["icon icon-shopping-bag"]))
                    (icons/shopping-bag)
                    (let [item-count (reduce (fn [sum sku]
                                               (let [store-is-open? (= :status.type/open
                                                                       (-> sku :store.item/_skus :store/_items :store/status :status/type))]
                                                 (if store-is-open? (inc sum) sum))) 0 (:user.cart/items cart))]
                      (when (pos? item-count)
                        (dom/span (css/add-class :badge) (count (:user.cart/items cart))))))))))]))

(defui Navbar
  static om/IQuery
  (query [_]
    (nav.common/query))
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

  (open-sidebar [this]
    #?(:cljs (let [body (first (web.utils/elements-by-class "page-container"))
                   {:keys [on-close-sidebar-fn]} (om/get-state this)]
               (web.utils/add-class-to-element body "sidebar-open")
               (.addEventListener js/document "click" on-close-sidebar-fn)
               (.addEventListener js/document "touchend" on-close-sidebar-fn)
               ;(.addEventListener js/document "touchstart" on-close-sidebar-fn)
               (om/update-state! this assoc :sidebar-open? true)
               )))

  (close-sidebar [this]
    #?(:cljs (let [body (first (web.utils/elements-by-class "page-container"))
                   {:keys [on-close-sidebar-fn]} (om/get-state this)]
               (web.utils/remove-class-to-element body "sidebar-open")
               (.removeEventListener js/document "click" on-close-sidebar-fn)
               (.removeEventListener js/document "touchend" on-close-sidebar-fn)
               ;(.removeEventListener js/document "touchstart" on-close-sidebar-fn)
               (om/update-state! this assoc :sidebar-open? false)
               )))
  #?(:cljs
     (shouldComponentUpdate [this props state]
                            (utils/should-update-when-route-is-loaded this props state)))
  (initLocalState [this]
    {:cart-open?             false
     :sidebar-open?          false
     :inline-sidebar-hidden? false
     #?@(:cljs [:on-click-event-fn #(.close-dropdown this %)
                :on-close-sidebar-fn #(.close-sidebar this)
                ])})
  (componentWillUnmount [this]
    #?(:cljs
       (let [{:keys [lock on-click-event-fn]} (om/get-state this)]
         (.removeEventListener js/document "click" on-click-event-fn))))

  (render [this]
    (let [{:query/keys [current-route navigation]
           :proxy/keys [loading-bar login-modal notification]} (om/props this)
          {:keys [route]} current-route]
      (debug "Navbar route: " route)
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

                ;(or (= route :coming-soon) (= route :coming-soon/sell))
                ;(landing-page-navbar this)

                (and (some? route) (or (= route :help) (= (namespace route) "help")))
                (help-navbar this)

                :else
                (standard-navbar this)))))

        (login/->LoginModal login-modal)
        (loading/->LoadingBar loading-bar)))))

(def ->Navbar (om/factory Navbar))
