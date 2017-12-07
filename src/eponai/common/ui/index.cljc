(ns eponai.common.ui.index
  (:require
    [eponai.common.ui.common :as common]
    [eponai.web.ui.photo :as photo]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug error]]
    [eponai.common.ui.dom :as dom :refer [div a]]
    [eponai.common.ui.elements.css :as css]
    [eponai.client.routes :as routes]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.content-item :as ci]
    [eponai.common.ui.router :as router]
    [eponai.common.ui.product :as product]
    [eponai.common.format.date :as date]
    [eponai.web.ui.button :as button]
    #?(:cljs
       [eponai.web.utils :as web.utils])
    [eponai.common.ui.stream :as stream]
    [eponai.common.photos :as photos]))

(defn scroll-horizontal [el-id & [direction]]
  #?(:cljs (let [element (web.utils/element-by-id el-id)]
             (if (= direction :left)
               (web.utils/scroll-horizontal-distance element (- 0 js/window.innerWidth) 500)
               (web.utils/scroll-horizontal-distance element js/window.innerWidth 500)))))

(defn collection-element [{:keys [href url title full? url-small photo-id alt]}]
  ;; Use the whole thing as hover elem
  (dom/a
    {:href    href
     :classes [:full :category-photo]}
    (photo/video-thumbnail
      (css/add-class :widescreen {:photo-id photo-id
                                  :alt alt})
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
     {:query/featured-vods (om/get-query ci/StoreVod)}
     {:query/featured-stores (om/get-query ci/StoreItem)}
     {:query/featured-streams (om/get-query ci/OnlineChannel)}
     {:query/auth [:db/id :user/email]}
     {:query/owned-store [:db/id
                          {:store/locality [:sulo-locality/path]}
                          {:store/status [:status/type]}
                          {:store/profile [:store.profile/name {:store.profile/photo [:photo/path]}]}
                          ;; to be able to query the store on the client side.
                          {:store/owners [{:store.owner/user [:db/id]}]}]}])
  Object
  (render [this]
    (let [{:query/keys [top-streams featured-streams featured-stores current-route featured-vods featured-items featured-home featured-men featured-art]} (om/props this)
          {:keys [route-params]} current-route]
      (dom/div
        nil
        (dom/div
          (->> {:id "sulo-index-container"}
               (css/add-class :landing-page))

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
            (when (seq featured-streams)
              (common/content-section {:href  (routes/url :live route-params)
                                       :class "online-channels"}
                                      (dom/span nil "LIVE right now")

                                      (if (not-empty featured-streams)
                                        (grid/row
                                          (->>
                                            (grid/columns-in-row {:small 2 :medium 3 :large 5}))
                                          (map (fn [c]
                                                 (grid/column
                                                   (css/add-class :online-stream)
                                                   (ci/->OnlineChannel c)))
                                               featured-streams))
                                        (dom/div (css/add-class :empty-container)
                                                 (dom/p (css/add-class :shoutout) "No live sessions at the moment :'(")))
                                      ""))


            (when (seq featured-vods)
              (common/content-section {:href  (routes/url :live route-params)
                                       :class "online-channels"}
                                      (dom/span nil "Latest Live streams")

                                      (dom/div
                                        nil
                                        (grid/row
                                          (->> {:id "content-row-videos"}
                                            (grid/columns-in-row {:small 2 :medium 3 :large 5}))
                                          (map (fn [p]
                                                 (grid/column
                                                   (css/add-class :online-stream)
                                                   (ci/->StoreVod p)))
                                               featured-vods))
                                        (dom/div
                                          (css/add-classes [:scroll-button-container :right])
                                          (dom/a
                                            (->> {:onClick #(scroll-horizontal "content-row-videos")}
                                                 (css/add-classes [:scroll-button :right]))
                                            (dom/i (css/add-classes [:fa :fa-chevron-right]))))
                                        (dom/div
                                          (css/add-classes [:scroll-button-container :left])
                                          (dom/a
                                            (->> {:onClick #(scroll-horizontal "content-row-videos" :left)}
                                                 (css/add-classes [:scroll-button :left]))
                                            (dom/i (css/add-classes [:fa :fa-chevron-left])))))
                                      ""))

            (dom/div
              (->>
                (css/add-classes [:gray :features-banner :banner :section]))
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
                                       :alt "Shop home"
                                       :title    "Home"})
                  )
                (grid/column
                  (->> (css/add-class :content-item)
                       (css/add-class :collection-item))
                  (collection-element {:href     (routes/url :browse/gender (merge route-params
                                                                                   {:sub-category "women"}))
                                       :photo-id "static/women-3"
                                       :alt "Shop women"
                                       :title    "Women"}))
                (grid/column
                  (->> (css/add-class :content-item)
                       (css/add-class :collection-item))
                  (collection-element {:href     (routes/url :browse/category (merge route-params
                                                                                     {:top-category "art"}))
                                       :alt "Shop art"
                                       :photo-id "static/collection-art-5"
                                       :title    "Art"}))
                (grid/column
                  (->> (css/add-class :content-item)
                       (css/add-class :collection-item))
                  (collection-element {:href     (routes/url :browse/gender (merge route-params
                                                                                   {:sub-category "men"}))
                                       :alt "Shop men"
                                       :photo-id "static/men-3"
                                       :title    "Men"}))))

            (common/content-section
              {:href  (routes/url :stores route-params)
               :class "new-brands"}
              (dom/span nil "Creatives")
              (dom/div
                {:classes ["sulo-items-container"]}

                (grid/row
                  (->> {:id    "content-row-brands"
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

                        ))))
                (dom/div
                  (css/add-classes [:scroll-button-container :right])
                  (dom/a
                    (->> {:onClick #(scroll-horizontal "content-row-brands")}
                         (css/add-classes [:scroll-button :right]))
                    (dom/i (css/add-classes [:fa :fa-chevron-right]))))
                (dom/div
                  (css/add-classes [:scroll-button-container :left])
                  (dom/a
                    (->> {:onClick #(scroll-horizontal "content-row-brands" :left)}
                         (css/add-classes [:scroll-button :left]))
                    (dom/i (css/add-classes [:fa :fa-chevron-left])))))
              "")
            (common/content-section
              {:href  (routes/url :browse/gender (merge route-params
                                                        {:sub-category "women"}))
               :class "products"}
              (dom/span nil "Products")
              (dom/div
                nil
                (grid/row
                  (->> {:id "products-section"}
                       (grid/columns-in-row {:small 2 :medium 4 :large 6}))
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
                        ))))

                (dom/div
                  (css/add-classes [:scroll-button-container :right])
                  (dom/a
                    (->> {:onClick #(scroll-horizontal "products-section")}
                         (css/add-classes [:scroll-button :right]))
                    (dom/i (css/add-classes [:fa :fa-chevron-right]))))
                (dom/div
                  (css/add-classes [:scroll-button-container :left])
                  (dom/a
                    (->> {:onClick #(scroll-horizontal "products-section" :left)}
                         (css/add-classes [:scroll-button :left]))
                    (dom/i (css/add-classes [:fa :fa-chevron-left])))))
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