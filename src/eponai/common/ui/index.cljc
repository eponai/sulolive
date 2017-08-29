(ns eponai.common.ui.index
  (:require
    [eponai.common.ui.common :as common]
    [eponai.web.ui.photo :as photo]
    [om.next :as om :refer [defui]]
    [om.dom]
    [taoensso.timbre :refer [debug error]]
    [eponai.common.ui.dom :as dom :refer [div a]]
    [eponai.common.ui.elements.css :as css]
    [eponai.client.routes :as routes]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.content-item :as ci]
    [eponai.common.ui.router :as router]
    [eponai.common.photos :as photos]
    #?(:cljs react-helmet)
    #?(:cljs react)))



;(defn banner [{:keys [color align] :as opts} primary secondary]
;  (let [align (or align :left)
;        color (or color :default)]
;    (dom/div #js {:className (str "banner " (name color))}
;      (grid/row
;        nil
;        (grid/column
;          (cond->> (->> (grid/column-size {:small 9 :medium 8})
;                        (css/text-align align))
;                   (= align :right)
;                   (grid/column-offset {:small 3 :medium 4}))
;          primary)
;        (grid/column
;          (css/align :right)
;          secondary)))))

(defn collection-element [{:keys [href url title full? url-small photo-id]}]
  ;; Use the whole thing as hover elem
  (dom/a
    {:href    href
     :classes [:full :category-photo]}
    (photo/video-thumbnail
      (css/add-class :widescreen {:photo-id photo-id})
      (photo/overlay
        nil (dom/div
              (->> (css/text-align :center))
              (dom/h4 nil title))))))

(defui Index
  static om/IQuery
  (query [_]
    [
     :query/current-route
     {:query/featured-items (om/get-query ci/ProductItem)}
     ;{:query/featured-women (om/get-query ci/ProductItem)}
     ;{:query/featured-home (om/get-query ci/ProductItem)}
     ;{:query/featured-men (om/get-query ci/ProductItem)}
     ;{:query/featured-art (om/get-query ci/ProductItem)}
     {:query/featured-stores (om/get-query ci/StoreItem)}
     {:query/featured-streams (om/get-query ci/OnlineChannel)}
     ;; {:query/top-streams (om/get-query ci/OnlineChannel)}
     {:query/auth [:db/id :user/email]}
     {:query/owned-store [:db/id
                          {:store/locality [:sulo-locality/path]}
                          {:store/status [:status/type]}
                          {:store/profile [:store.profile/name {:store.profile/photo [:photo/path]}]}
                          ;; to be able to query the store on the client side.
                          {:store/owners [{:store.owner/user [:db/id]}]}]}])
  Object
  (render [this]
    (let [{:query/keys [top-streams featured-streams featured-stores current-route online-stores featured-items featured-home featured-men featured-art]} (om/props this)
          {:keys [route-params]} current-route]
      ;(debug "Featured women products: " featured-women)

      #?(:cljs (debug "react-helmet: " react-helmet))

      (dom/div
        nil
        #?(:cljs
           (om.dom/create-element (.-Helmet react-helmet)
                                  #js {}
                                  (om.dom/title #js {} "My TiTLE x")))
        (dom/div
          (->> {:id "sulo-index-container"}
               (css/add-class :landing-page))

          ;(common/city-banner this locations)
          (dom/div
            (css/add-class :hero)
            (dom/div
              (css/add-class :hero-background)
              #?(:cljs
                 (dom/video {:autoPlay true :loop true :muted true :poster (photos/transform "static/sulo-landing-low-2-thumbnail" :transformation/cover)}
                            (dom/source {:src "https://d30slnyi7gxcwc.cloudfront.net/site/videos/sulo-landing-low-2.mp4"}))
                 :clj
                 (photo/cover {:photo-id "static/sulo-landing-low-2-thumbnail"})))
            (dom/div
              (css/add-class :hero-content)
              (dom/div
                (->> (css/add-class :va-container)
                     (css/text-align :center))
                (dom/h1 (css/show-for-sr) "SULO Live")
                (dom/h2 (css/add-class :jumbo-header) "Know their story")
                (dom/p (css/add-classes [:jumbo-lead :lead]) "Shop from creatives you get to know through their LIVE streams."))))

          (dom/div
            (css/add-class :sections)
            (when (not-empty featured-streams)
              (common/content-section {:href  (routes/url :live route-params)
                                       :class "online-channels"}
                                      (dom/span nil "LIVE right now")

                                      (grid/row
                                        (->>
                                          (grid/columns-in-row {:small 2 :medium 3 :large 5}))
                                        ;(grid/column
                                        ;  (css/add-class :online-streams))
                                        (map (fn [c]
                                               (grid/column
                                                 (css/add-class :online-stream)
                                                 (ci/->OnlineChannel c)))
                                             featured-streams))
                                      ""))


            ;(when (<= 5 (count featured-streams))
            ;  (common/content-section {:href  (routes/url :live route-params)
            ;                           :class "online-channels"}
            ;                          (dom/a {:href (routes/url :live route-params)}
            ;                                 (dom/span nil "Recent Live streams"))
            ;
            ;                          (grid/row
            ;                            (->>
            ;                              (grid/columns-in-row {:small 2 :medium 3 :large 5}))
            ;                            ;(grid/column
            ;                            ;  (css/add-class :online-streams))
            ;                            (map (fn [c]
            ;                                   (grid/column
            ;                                     (css/add-class :online-stream)
            ;                                     (ci/->OnlineChannel c)))
            ;                                 featured-streams))
            ;                          ""))

            ;(when (<= 5 (count featured-streams))
            ;  (common/content-section {:href  (routes/url :live route-params)
            ;                           :class "online-channels"}
            ;                          (dom/a {:href (routes/url :live route-params)}
            ;                                 (dom/span nil "Popular"))
            ;
            ;                          (grid/row
            ;                            (->>
            ;                              (grid/columns-in-row {:small 2 :medium 4 :large 5}))
            ;                            ;(grid/column
            ;                            ;  (css/add-class :online-streams))
            ;                            (map (fn [c]
            ;                                   (grid/column
            ;                                     (css/add-class :online-stream)
            ;                                     (ci/->OnlineChannel c)))
            ;                                 featured-streams))
            ;                          ""))
            ;(when (not-empty featured-streams))
            (dom/div
              (->>
                (css/add-classes [:gray :features-banner :banner :section]))
              ;(dom/div
              ;  (->> (css/add-class :section-title)
              ;       (css/text-align :center)))
              (grid/row
                (->> (grid/columns-in-row {:small 1 :medium 3})
                     (css/text-align :center))
                (grid/column
                  (css/add-classes [:content-item :feature-item])
                  (dom/div (css/add-classes [:icon :icon-watch]))
                  (dom/div
                    nil
                    (dom/p (css/add-classes [:lead :jumbo-lead :banner :sulo-dark]) "Watch")
                    (dom/p nil (dom/span nil "Watch your favorite creatives work and follow their creative process to finished product."))))
                (grid/column
                  (css/add-classes [:content-item :feature-item])
                  (dom/div (css/add-classes [:icon :icon-community]))
                  (dom/div
                    nil
                    (dom/p (css/add-classes [:lead :jumbo-lead :banner :sulo-dark]) "Engage")
                    (dom/p nil (dom/span nil "Ask questions or share thoughts via live chat rooms on their streams."))))
                (grid/column
                  (css/add-classes [:content-item :feature-item])
                  (dom/div (css/add-classes [:icon :icon-shop :blue]))
                  (dom/div
                    nil
                    (dom/p (css/add-classes [:lead :jumbo-lead :banner :sulo-dark]) "Shop")
                    (dom/p nil (dom/span nil "Shop the products you've seen take shape, from creatives you know.")))))
              )
            ;(common/mobile-app-banner this)

            ;<!-- LightWidget WIDGET --><script src="//lightwidget.com/widgets/lightwidget.js"></script><iframe src="//lightwidget.com/widgets/518cc674e6265d0fa7aae74a603e7c25.html" scrolling="no" allowtransparency="true" class="lightwidget-widget" style="width: 100%; border: 0; overflow: hidden;"></iframe>

            (dom/div
              (->> (css/add-classes [:collections :section])
                   (css/text-align :center))

              (dom/h3 (css/add-class :section-header) "Shop by collection")



              (grid/row
                (->> (grid/columns-in-row {:small 2 :large 4})
                     )
                (grid/column
                  (->> (css/add-class :content-item)
                       (css/add-class :collection-item))
                  (collection-element {:href     (routes/url :browse/category (merge route-params
                                                                                     {:top-category "home"}))
                                       :photo-id "static/home-4"
                                       :title    "Home"})
                  )
                (grid/column
                  (->> (css/add-class :content-item)
                       (css/add-class :collection-item))
                  (collection-element {:href     (routes/url :browse/gender (merge route-params
                                                                                   {:sub-category "women"}))
                                       :photo-id "static/women-3"
                                       :title    "Women"}))
                (grid/column
                  (->> (css/add-class :content-item)
                       (css/add-class :collection-item))
                  (collection-element {:href     (routes/url :browse/category (merge route-params
                                                                                     {:top-category "art"}))
                                       :photo-id "static/collection-art-5"
                                       :title    "Art"}))
                (grid/column
                  (->> (css/add-class :content-item)
                       (css/add-class :collection-item))
                  (collection-element {:href     (routes/url :browse/gender (merge route-params
                                                                                   {:sub-category "men"}))
                                       :photo-id "static/men-3"
                                       :title    "Men"}))))

            (common/content-section
              {:href  (routes/url :stores route-params)
               :class "new-brands"}
              (dom/span nil "Creatives")
              ;(grid/row-column
              ;  (css/text-align :center))
              ;(dom/div
              ;  (css/add-class :section-title)
              ;  (dom/h2 nil "New brands"))
              (dom/div
                {:classes ["sulo-items-container"]}
                ;(letfn [(scroll-right [] #?(:cljs
                ;                               (let [row (web.utils/element-by-id "content-row-products")
                ;                                     cols (array-seq (.-children row))]
                ;                                 (web.utils/scroll-horizontal-to row (second cols) 250)
                ;                                 (debug "Scroll")
                ;                                 )))
                ;        (scroll-left [] #?(:cljs
                ;                            (let [row (web.utils/element-by-id "content-row-products")
                ;                                  columns (array-seq (.-children row))]
                ;                              (debug "Scroll")
                ;                              (web.utils/scroll-horizontal-to row (first columns) 250))))]
                ;  [
                ;   (dom/a {:onClick scroll-right}
                ;          (dom/span nil "Next"))
                ;   (dom/a {:onClick scroll-left}
                ;          (dom/span nil "Previous"))])

                (grid/row
                  (->> {:id    "content-row-products"
                        :style {:paddingLeft 20}}
                       (grid/columns-in-row {:small 2 :medium 4 :large 7}))
                  (map (fn [store]
                         (let [store-name (get-in store [:store/profile :store.profile/name])]
                           (grid/column
                             nil
                             (ci/->StoreItem store))))
                       (take 12 featured-stores))
                  (grid/column
                    nil
                    (dom/div
                      (css/add-classes [:content-item :see-more-item])
                      (dom/a
                        {:href (routes/url :stores route-params)}
                        (dom/div (css/add-class :container)
                                 (dom/div (css/add-class :content) (dom/h6 nil "See more")))

                        )))))
              "")

            (common/content-section
              {:href  (routes/url :browse/gender (merge route-params
                                                        {:sub-category "women"}))
               :class "products"}
              (dom/span nil "Products")
              (dom/div
                nil
                (grid/row
                  (->>
                    (grid/columns-in-row {:small 2 :medium 4 :large 6}))
                  ;(grid/column
                  ;  (css/add-class :online-streams))
                  (map-indexed
                    (fn [i p]
                      (grid/column
                        nil
                        (ci/->ProductItem (om/computed p
                                                       {:open-url?     true
                                                        :current-route current-route}))))
                    (take 10 featured-items))
                  (grid/column
                    nil
                    (dom/div
                      (css/add-classes [:content-item :see-more-item])
                      (dom/a
                        {:href (routes/url :browse/gender (merge route-params
                                                                 {:sub-category "women"}))}
                        (dom/div (css/add-class :container)
                                 (dom/div (css/add-class :content) (dom/h6 nil "See more")))
                        )))))
              "")




            (common/sell-on-sulo this)



            (common/content-section
              {:class "sulo-instagram"}
              (dom/a {:href "https://www.instagram.com/sulolive/"} "@sulolive on Instagram")
              (dom/iframe {:src               "//lightwidget.com/widgets/ce86c110a4085157afb3cee3e869dec0.html"
                           ; GRID: "//lightwidget.com/widgets/1e49e2e8989a5331aab4285cd32905c0.html"
                           ; SLIDESHOW 6 photos: "//lightwidget.com/widgets/ffa8e755bbbb550d9072499fadcd6083.html"
                           :scrolling         "no"
                           :allowTransparency true
                           :className         "lightwidget-widget"
                           :style             {:width    "100%"
                                               :border   "0"
                                               :overflow "hidden"}})
              "")))))))


(def ->Index (om/factory Index))

(router/register-component :index Index)