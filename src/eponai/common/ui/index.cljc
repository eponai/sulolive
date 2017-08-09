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
    [eponai.common.ui.stream :as stream]))

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
    (photo/photo {:photo-id photo-id}
                 (photo/overlay
                   nil (dom/div
                         (->> (css/text-align :center))
                         (dom/h4 nil title))))))

(defui Index
  static om/IQuery
  (query [_]
    [:query/locations
     :query/current-route
     {:query/featured-items (om/get-query ci/ProductItem)}
     ;{:query/featured-women (om/get-query ci/ProductItem)}
     ;{:query/featured-home (om/get-query ci/ProductItem)}
     ;{:query/featured-men (om/get-query ci/ProductItem)}
     ;{:query/featured-art (om/get-query ci/ProductItem)}
     {:query/featured-stores (om/get-query ci/StoreItem)}
     {:query/featured-streams (om/get-query ci/OnlineChannel)}
     {:query/top-streams (om/get-query ci/OnlineChannel)}
     {:query/auth [:db/id :user/email]}
     {:query/owned-store [:db/id
                          {:store/locality [:sulo-locality/path]}
                          {:store/status [:status/type]}
                          {:store/profile [:store.profile/name {:store.profile/photo [:photo/path]}]}
                          ;; to be able to query the store on the client side.
                          {:store/owners [{:store.owner/user [:db/id]}]}]}
     {:query/online-stores (om/get-query ci/StoreItem)}])
  Object
  (render [this]
    (let [{:proxy/keys [navbar footer]
           :query/keys [locations top-streams featured-streams featured-stores current-route online-stores featured-items featured-home featured-men featured-art]} (om/props this)
          {:keys [route-params]} current-route
          streaming-stores (set (map #(get-in % [:stream/store :db/id]) featured-streams))
          online-not-live (remove #(contains? streaming-stores (:db/id %)) online-stores)
          online-right-now (filter #(let [online (-> % :store/owners :store.owner/user :user/online?)]
                                     (or (= true online)
                                         (> 60000 (- (date/current-millis) online))))
                                   online-not-live)
          online-items (vec (concat (map (fn [c]
                                           (grid/column
                                             (css/add-class :online-stream)
                                             (ci/->OnlineChannel c)))
                                         featured-streams)
                                    (map (fn [c]
                                           (grid/column
                                             nil
                                             (ci/->StoreItem c)))
                                         online-not-live)))
          online-items (if (<= 8 (count online-items))
                         (take 8 online-items)
                         (take 4 online-items))

          featured-live (first (sort-by #(get-in % [:stream/store :store/visitor-count]) top-streams))]
      ;(debug "Featured women products: " featured-women)


      (dom/div
        nil
        (dom/div {:id "sulo-index-container"}

                 ;(common/city-banner this locations)
                 (dom/div
                   (css/add-class :hero)
                   (dom/div
                     (css/add-class :hero-background)
                     (dom/video {:autoPlay true}
                                (dom/source {:src "https://a0.muscache.com/airbnb/static/P1-background-vid-compressed-2.mp4"})))
                   (dom/div
                     (css/add-class :hero-content)
                     (dom/div
                       (->> (css/add-class :va-container)
                            (css/text-align :center))
                       (dom/h1 (css/show-for-sr) "SULO Live")
                       (dom/h2 (css/add-class :jumbo-header) "Know their story")
                       (dom/p (css/add-classes [:jumbo-lead :lead]) "Shop from brands you get to know by engaging on their LIVE streams.")))
                   )

                 (dom/div
                   (css/add-class :sections)

                   (when (not-empty featured-streams)
                     (common/content-section {:href  (routes/url :live route-params)
                                              :class "online-channels"}
                                             (dom/a {:href (routes/url :live route-params)}
                                                    (dom/span nil "LIVE right now"))

                                             (grid/row
                                               (->>
                                                 (grid/columns-in-row {:small 2 :medium 4 :large 5}))
                                               ;(grid/column
                                               ;  (css/add-class :online-streams))
                                               (map (fn [c]
                                                      (grid/column
                                                        (css/add-class :online-stream)
                                                        (ci/->OnlineChannel c)))
                                                    featured-streams))
                                             ""))

                   (when (not-empty featured-streams)
                     (common/content-section {:href  (routes/url :live route-params)
                                              :class "online-channels"}
                                             (dom/a {:href (routes/url :live route-params)}
                                                    (dom/span nil "Recent Live streams"))

                                             (grid/row
                                               (->>
                                                 (grid/columns-in-row {:small 2 :medium 4 :large 5}))
                                               ;(grid/column
                                               ;  (css/add-class :online-streams))
                                               (map (fn [c]
                                                      (grid/column
                                                        (css/add-class :online-stream)
                                                        (ci/->OnlineChannel c)))
                                                    featured-streams))
                                             ""))

                   (when (not-empty featured-streams)
                     (common/content-section {:href  (routes/url :live route-params)
                                              :class "online-channels"}
                                             (dom/a {:href (routes/url :live route-params)}
                                                    (dom/span nil "Popular"))

                                             (grid/row
                                               (->>
                                                 (grid/columns-in-row {:small 2 :medium 4 :large 5}))
                                               ;(grid/column
                                               ;  (css/add-class :online-streams))
                                               (map (fn [c]
                                                      (grid/column
                                                        (css/add-class :online-stream)
                                                        (ci/->OnlineChannel c)))
                                                    featured-streams))
                                             ""))
                   (common/mobile-app-banner this)
                   ;(when featured-live
                   ;  (common/content-section
                   ;    {:href  (routes/store-url (:stream/store featured-live) :store)
                   ;     :class "featured"}
                   ;    (dom/a {:href (routes/store-url (:stream/store featured-live) :store)} (dom/span nil "Live right now"))
                   ;    (dom/div
                   ;      nil
                   ;      (grid/row
                   ;        (css/align :center)
                   ;        (grid/column
                   ;          (grid/column-size {:small 6 :medium 4})
                   ;          (photo/store-photo (:stream/store featured-live) nil))
                   ;        (grid/column
                   ;          (->> (grid/column-size {:small 6 :medium 4})
                   ;               (css/text-align :right))
                   ;          (button/store-navigation-default {:href (routes/store-url (:stream/store featured-live) :store)} (dom/span nil "Visit store"))))
                   ;      (grid/row
                   ;        (->> (css/align :center)
                   ;             (css/text-align :center))
                   ;        (grid/column
                   ;          (grid/column-size {:small 12 :medium 8})
                   ;          (dom/div
                   ;            (css/add-class :live-container)
                   ;            (stream/->Stream (om/computed {:stream featured-live}
                   ;                                          {:store (:stream/store featured-live)}))))))
                   ;    "")
                   ;
                   ;  )









                   ;(when (not-empty featured-men)
                   ;  (common/content-section
                   ;    {:href  (routes/url :browse/gender (merge route-params
                   ;                                              {:sub-category "men"}))
                   ;     :class "products"}
                   ;    (dom/a {:href (routes/url :browse/gender (merge route-params
                   ;                                                    {:sub-category "men"}))}
                   ;           (dom/span nil "Men"))
                   ;    (dom/div
                   ;      nil
                   ;      (grid/row
                   ;        (->>
                   ;          (grid/columns-in-row {:small 2 :medium 4 :large 5}))
                   ;        ;(grid/column
                   ;        ;  (css/add-class :online-streams))
                   ;        (map-indexed
                   ;          (fn [i p]
                   ;            (grid/column
                   ;              (cond
                   ;                (< 7 i)
                   ;                (css/show-for :large)
                   ;                (< 3 i)
                   ;                (css/show-for :medium))
                   ;              (ci/->ProductItem (om/computed p
                   ;                                             {:open-url?     true
                   ;                                              :current-route current-route}))))
                   ;          (take 10 featured-men))))
                   ;    "Browse men"))

                   ;(when (not-empty featured-home)
                   ;  (common/content-section
                   ;    {:href  (routes/url :browse/category (merge route-params
                   ;                                                {:top-category "home"}))
                   ;     :class "products"}
                   ;    (dom/a {:href (routes/url :browse/category (merge route-params
                   ;                                                      {:top-category "home"}))}
                   ;           (dom/span nil "Home"))
                   ;    (dom/div
                   ;      nil
                   ;      (grid/row
                   ;        (->>
                   ;          (grid/columns-in-row {:small 2 :medium 4 :large 5}))
                   ;        ;(grid/column
                   ;        ;  (css/add-class :online-streams))
                   ;        (map-indexed
                   ;          (fn [i p]
                   ;            (grid/column
                   ;              (cond
                   ;                (< 7 i)
                   ;                (css/show-for :large)
                   ;                (< 3 i)
                   ;                (css/show-for :medium))
                   ;              (ci/->ProductItem (om/computed p
                   ;                                             {:open-url?     true
                   ;                                              :current-route current-route}))))
                   ;          (take 10 featured-home))))
                   ;    "Browse home"))
                   ;(when (not-empty featured-art)
                   ;  (common/content-section
                   ;    {:href  (routes/url :browse/category (merge route-params
                   ;                                                {:top-category "art"}))
                   ;     :class "products"}
                   ;    (dom/a {:href (routes/url :browse/category (merge route-params
                   ;                                                      {:top-category "art"}))} "Art")
                   ;    (dom/div
                   ;      nil
                   ;      (grid/row
                   ;        (->>
                   ;          (grid/columns-in-row {:small 2 :medium 4 :large 5}))
                   ;        ;(grid/column
                   ;        ;  (css/add-class :online-streams))
                   ;        (map-indexed
                   ;          (fn [i p]
                   ;            (grid/column
                   ;              (cond
                   ;                (< 7 i)
                   ;                (css/show-for :large)
                   ;                (< 3 i)
                   ;                (css/show-for :medium))
                   ;              (ci/->ProductItem (om/computed p
                   ;                                             {:open-url?     true
                   ;                                              :current-route current-route}))))
                   ;          (if (<= 10 (count featured-art))
                   ;            (take 10 featured-art)
                   ;            (take 5 featured-art)))))
                   ;    "Browse art"))





                   (common/content-section
                     {:href  (routes/url :stores route-params)
                      :class "new-brands"}
                     (dom/a {:href (routes/url :stores route-params)} (dom/span nil "Brands"))
                     ;(grid/row-column
                     ;  (css/text-align :center))
                     ;(dom/div
                     ;  (css/add-class :section-title)
                     ;  (dom/h2 nil "New brands"))
                     (dom/div
                       {:classes ["sulo-items-container"]}
                       (grid/row
                         (grid/columns-in-row {:small 2 :medium 4 :large 6})
                         (map (fn [store]
                                (let [store-name (get-in store [:store/profile :store.profile/name])]
                                  (grid/column
                                    nil
                                    (ci/->StoreItem store))))
                              (take 4 featured-stores))))
                     "")

                   (when (not-empty featured-items)
                     (common/content-section
                       {:href  (routes/url :browse/gender (merge route-params
                                                                 {:sub-category "women"}))
                        :class "products"}
                       (dom/a {:href (routes/url :browse/gender (merge route-params
                                                                       {:sub-category "women"}))}
                              (dom/span nil "Products"))
                       (dom/div
                         nil
                         (grid/row
                           (->> (css/add-class :collapse)
                             (grid/columns-in-row {:small 2 :medium 4 :large 6}))
                           ;(grid/column
                           ;  (css/add-class :online-streams))
                           (map-indexed
                             (fn [i p]
                               (grid/column
                                 (cond
                                   (< 7 i)
                                   (css/show-for :large)
                                   (< 3 i)
                                   (css/show-for :medium))
                                 (ci/->ProductItem (om/computed p
                                                                {:open-url?     true
                                                                 :current-route current-route}))))
                             (take 10 featured-items))))
                       ""))

                   (common/sell-on-sulo this)
                   (dom/div
                     (->> (css/add-classes [:collections :section])
                          (css/text-align :center))
                     
                     (dom/h3 (css/add-class :section-header) "Shop by collection")



                     (grid/row
                       (->> (grid/columns-in-row {:small 2 :medium 4})
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



                   ;(common/content-section {:href  (routes/url :browse/all-items route-params)
                   ;                         :class "new-arrivals"}
                   ;                        "New products"
                   ;                        (grid/row
                   ;                          (grid/columns-in-row {:small 2 :medium 4 :large 5})
                   ;                          (map
                   ;                            (fn [p]
                   ;                              (grid/column
                   ;                                (css/add-class :new-arrival-item)
                   ;                                (ci/->ProductItem (om/computed p
                   ;                                                               {:open-url?     true
                   ;                                                                :current-route current-route}))))
                   ;                            (take 5 featured-items)))
                   ;                        "See more products")
                   ))))))


(def ->Index (om/factory Index))

(router/register-component :index Index)