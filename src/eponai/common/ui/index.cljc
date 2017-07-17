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
    [eponai.common.format.date :as date]))

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
                         (dom/h3 nil title))))))

(defui Index
  static om/IQuery
  (query [_]
    [:query/locations
     :query/current-route
     {:query/featured-items (om/get-query ci/ProductItem)}
     {:query/featured-stores (om/get-query ci/StoreItem)}
     {:query/featured-streams (om/get-query ci/OnlineChannel)}
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
           :query/keys [locations featured-items featured-streams featured-stores current-route online-stores]} (om/props this)
          {:keys [route-params]} current-route
          streaming-stores (set (map #(get-in % [:stream/store :db/id]) featured-streams))
          online-not-live (remove #(contains? streaming-stores (:db/id %)) online-stores)
          online-right-now (filter #(let [online (-> % :store/owners :store.owner/user :user/online?)]
                                     (or (= true online)
                                         (> 60000 (- (date/current-millis) online))))
                                   online-not-live)]

      (dom/div
        nil
        (dom/div {:id "sulo-index-container"}

                 (common/city-banner this locations)
                 ;(my-dom/div
                 ;  (css/add-class :intro-header)
                 ;  (grid/row
                 ;    (css/align :middle)
                 ;    (grid/column
                 ;      (grid/column-size {:small 12 :medium 6})
                 ;      (my-dom/h1
                 ;        (css/add-class :header)
                 ;        (dom/i #js {:className "fa fa-map-marker"})
                 ;        (dom/span nil locations)))
                 ;    (grid/column
                 ;      nil
                 ;      (my-dom/div
                 ;        (css/add-class :input-container)
                 ;        (search-bar/->SearchBar {:ref             (str ::search-bar-ref)
                 ;                                 :placeholder     "What are you looking for?"
                 ;                                 :mixpanel-source "index"
                 ;                                 :classes         [:drop-shadow]})
                 ;        (button/button
                 ;          (->> (button/expanded {:onClick (fn []
                 ;                                            (let [search-bar (om/react-ref this (str ::search-bar-ref))]
                 ;                                              (when (nil? search-bar)
                 ;                                                (error "NO SEARCH BAR :( " this))
                 ;                                              (search-bar/trigger-search! search-bar)))})
                 ;               (css/add-classes [:drop-shadow]))
                 ;          (dom/span nil "Search"))))))

                 (dom/div
                   (css/add-class :sections)
                   (cond (not-empty featured-streams)
                         (common/content-section {:href  (routes/url :live route-params)
                                                  :class "online-channels"}
                                                 "Online right now"
                                                 (grid/row
                                                   (->>
                                                     (grid/columns-in-row {:small 2 :medium 4}))
                                                   ;(grid/column
                                                   ;  (css/add-class :online-streams))
                                                   (map (fn [c]
                                                          (if (:store/featured c)
                                                            (grid/column
                                                              nil
                                                              (ci/->StoreItem c))
                                                            (grid/column
                                                              (css/add-class :online-stream)
                                                              (ci/->OnlineChannel c))))
                                                        (cond (<= 8 (count featured-streams))
                                                              (take 8 featured-streams)
                                                              (<= 4 (count featured-streams))
                                                              (take 4 featured-streams)
                                                              :else
                                                              (take 4 (into (vec featured-streams) online-not-live)))))
                                                 "See more")
                         (not-empty online-right-now)
                         (common/content-section {:href  (routes/url :live route-params)
                                                  :class "online-channels"}
                                                 "Online right now"
                                                 (grid/row
                                                   (->>
                                                     (grid/columns-in-row {:small 2 :medium 4}))
                                                   ;(grid/column
                                                   ;  (css/add-class :online-streams))
                                                   (map (fn [store]
                                                          (grid/column
                                                            nil
                                                            (ci/->StoreItem store)))
                                                        (take 4 online-right-now)))
                                                 "See more"))



                   (common/content-section {:class "collections"}
                                           "Shop by collection"
                                           (div nil
                                                (grid/row
                                                  (grid/columns-in-row {:small 1 :medium 2})
                                                  (grid/column
                                                    (->> (css/add-class :content-item)
                                                         (css/add-class :collection-item))
                                                    (collection-element {:href     (routes/url :browse/category (merge route-params
                                                                                                                       {:top-category "home"}))
                                                                         :photo-id "static/home-4"
                                                                         :title    "Home"}))
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
                                           ;(map (fn [s t]
                                           ;       (collection-element {:url (first (:store/featured-img-src s))
                                           ;                            :title t}))
                                           ;     featured-stores
                                           ;     ["Home" "Kids" "Women" "Men"])
                                           ""
                                           )

                   (common/content-section
                     {:href  (routes/url :stores route-params)
                      :class "new-brands"}
                     "New stores"
                     ;(grid/row-column
                     ;  (css/text-align :center))
                     ;(dom/div
                     ;  (css/add-class :section-title)
                     ;  (dom/h2 nil "New brands"))
                     (dom/div
                       {:classes ["sulo-items-container"]}
                       (grid/row
                         (grid/columns-in-row {:small 2 :medium 4})
                         (map (fn [store]
                                (let [store-name (get-in store [:store/profile :store.profile/name])]
                                  (grid/column
                                    nil
                                    (ci/->StoreItem store))))
                              (take 4 featured-stores))))
                     "See all stores")

                   (common/content-section {:href  (routes/url :browse/all-items route-params)
                                            :class "new-arrivals"}
                                           "New products"
                                           (grid/row
                                             (grid/columns-in-row {:small 2 :medium 4 :large 5})
                                             (map
                                               (fn [p]
                                                 (grid/column
                                                   (css/add-class :new-arrival-item)
                                                   (ci/->ProductItem (om/computed p
                                                                                  {:open-url?     true
                                                                                   :current-route current-route}))))
                                               (take 5 featured-items)))
                                           "See more products")



                   (common/sell-on-sulo this)))))))


(def ->Index (om/factory Index))

(router/register-component :index Index)