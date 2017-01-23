(ns eponai.common.ui.navbar
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.utils :as ui-utils]
    [eponai.common.ui.elements.css :as css]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.photo :as photo]
    [taoensso.timbre :refer [debug error]]
    [eponai.common.ui.elements.menu :as menu]))

(defn cart-dropdown [{:keys [cart/items cart/price]}]
  (dom/div #js {:className "cart-container dropdown-pane"}
    (apply menu/vertical
           {:classes [::css/cart]}
           (map (fn [i]
                  (menu/item-link
                    {:href    (str "/goods/" (:db/id i))
                     :classes [:cart-link]}
                    ;(dom/div #js {})
                    (photo/square
                      {:src (:item/img-src i)})
                    (dom/div #js {:className ""}
                      (dom/div #js {:className "content-item-title-section"}
                        (dom/p nil (dom/span #js {:className "name"} (:item/name i))))
                      (dom/div #js {:className "content-item-subtitle-section"}
                        (dom/strong #js {:className "price"}
                                  (ui-utils/two-decimal-price (:item/price i)))))))
                (take 3 items)))

    (dom/div #js {:className "callout transparent"}
      (if (< 3 (count items))
        (dom/small nil (str "You have " (- (count items) 3) " more item(s) in your bag"))
        (dom/small nil (str "You have " (count items) " item(s) in your bag")))
      (dom/h5 nil "Total: " (dom/strong nil (ui-utils/two-decimal-price price))))
    (dom/a #js {:className "button expanded hollow gray"
                :href      "/checkout"} "View My Bag")))

(defn category-dropdown []
  (dom/div #js {:className "dropdown-pane"}
    (menu/vertical
      {:classes [::css/categories]}
      (menu/item-link
        {:href (str "/goods?category=clothing")}
        (dom/span nil "Clothing"))
      (menu/item-link
        {:href (str "/goods?category=accessories")}
        (dom/span nil "Accessories"))
      (menu/item-link
        {:href (str "/goods?category=home")}
        (dom/span nil "Home")))))

(defn user-dropdown [component user]
  (dom/div #js {:className "dropdown-pane"}
    (dom/ul #js {:className "menu vertical"}
            (dom/li nil
                    (dom/a #js {:href "/profile"}
                           "My Profile"))
            (dom/li nil
                    (dom/a #js {:href "/logout"}
                           "Sign Out")))))

(defui Navbar
  static om/IQuery
  (query [_]
    [{:query/cart [:cart/price
                   {:cart/items [:item/price
                                 :item/img-src
                                 :item/name
                                 {:item/store [:store/name :store/rating :store/photo :store/review-count]}]}]}
     :query/auth])
  Object
  #?(:cljs
     (open-signin [this]
                  (debug "Open signin")
             (let [{:keys [lock]} (om/get-state this)
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

  (initLocalState [_]
    {:cart-open? false
     :on-scroll-fn #(debug "Did scroll: " %)})
  (componentWillUnmount [this]
    #?(:cljs
       (let [{:keys [lock on-scroll-fn]} (om/get-state this)]
         (.removeEventListener js/document.documentElement "scroll" on-scroll-fn))))
  #?(:cljs
     (componentDidMount
       [this]
       (let [{:keys [on-scroll-fn]} (om/get-state this)]
         (when js/Auth0LockPasswordless
           (let [lock (new js/Auth0LockPasswordless "JMqCBngHgOcSYBwlVCG2htrKxQFldzDh" "sulo.auth0.com")]
             (om/update-state! this assoc :lock lock)))
         (.addEventListener js/document.documentElement "scroll" on-scroll-fn)
         (om/update-state! this assoc :did-mount? true))))

  (render [this]
    (let [{:keys [cart-open? signin-open? did-mount?]} (om/get-state this)
          {:keys [query/cart query/auth]} (om/props this)
          {:keys [coming-soon?]} (om/get-computed this)]

      (dom/div #js {:id "sulo-navbar"}
        (dom/nav #js {:className "navbar-container"}
                 (dom/div #js {:className "top-bar navbar"}
                   (dom/div #js {:className "top-bar-left"}
                     (menu/horizontal
                       nil
                       (menu/item-link {:href (if coming-soon? "" "/")
                                        :id   "navbar-brand"}
                                       (dom/span nil "Sulo"))
                       (menu/item-link
                         (->> (css/add-class ::css/highlight {:href (if coming-soon? "" "/streams")})
                              (css/add-class :navbar-live))
                         (my-dom/strong
                           (css/hide-for {:size :small :only? true})
                           ;; Wrap in span for server and client to render the same html
                           (dom/span nil "Live"))
                         (my-dom/div
                           (css/show-for {:size :small :only? true})
                           (dom/i #js {:className "fa fa-video-camera fa-fw"})))

                       (menu/item-dropdown
                         (->> {:dropdown (category-dropdown)}
                              (css/hide-for {:size :large})
                              (css/add-class :category))
                         (dom/span nil "Shop")
                         ;(dom/i #js {:className "fa fa-caret-down fa-fw"})
                         )

                       (menu/item-link
                         (->> (css/add-class :category {:href ""})
                              (css/show-for {:size :large}))
                         (dom/span nil "Women"))
                       (menu/item-link
                         (->> (css/add-class :category {:href ""})
                              (css/show-for {:size :large}))
                         (dom/span nil "Men"))
                       (menu/item-link
                         (->> (css/add-class :category {:href ""})
                              (css/show-for {:size :large}))
                         (dom/span nil "Kids"))
                       (menu/item-link
                         (->> (css/add-class :category {:href ""})
                              (css/show-for {:size :large}))
                         (dom/span nil "Home"))
                       (menu/item-link
                         (->> (css/add-class :category {:href ""})
                              (css/show-for {:size :large}))
                         (dom/span nil "Art"))
                       ))

                   (dom/div #js {:className "top-bar-right"}
                     (if coming-soon?
                       (menu/horizontal
                         nil
                         (menu/item-link
                           (css/add-class :contact {:href "mailto:hello@sulo.live"})
                           (my-dom/span (css/show-for {:size :medium}) (dom/span nil "Sell on SULO? Send us an email"))
                           (dom/i #js {:className "fa fa-envelope-o fa-fw"})))

                       (menu/horizontal
                         nil
                         (menu/item nil
                                    (my-dom/a
                                      (->> {:id "search-icon"}
                                           (css/show-for {:size :small :only? true}))
                                      (dom/i #js {:className "fa fa-search fa-fw"}))
                                    (my-dom/div
                                      (css/hide-for {:size :small :only? true})
                                      (dom/input #js {:type        "text"
                                                      :placeholder "Search on SULO..."
                                                      :onKeyDown   (fn [e]
                                                                     #?(:cljs
                                                                        (when (= 13 (.. e -keyCode))
                                                                          (let [search-string (.. e -target -value)]
                                                                            (set! js/window.location (str "/goods?search=" search-string))))))})))
                         ;(if (some? (not-empty auth))
                         ;  (menu/item-link nil (dom/a nil "You"))
                         ;  (menu/item nil (dom/a #js {:className "button hollow"
                         ;                             :onClick   #(do
                         ;                                          #?(:cljs
                         ;                                             (.open-signin this)))} "Sign in")))
                         (menu/item-dropdown
                           {:dropdown (user-dropdown this nil)}
                           (dom/i #js {:className "fa fa-user fa-fw"}))
                         (if did-mount?
                           (menu/item-dropdown
                             {:dropdown (cart-dropdown cart)
                              :href     "/checkout"}
                             (dom/i #js {:className "fa fa-shopping-cart fa-fw"}))
                           (menu/item-dropdown
                             {:href     "/checkout"}
                             (dom/i #js {:className "fa fa-shopping-cart fa-fw"}))))))))))))
(def ->Navbar (om/factory Navbar))

(defn navbar [props]
  (->Navbar props))