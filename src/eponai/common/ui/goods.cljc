(ns eponai.common.ui.goods
  (:require
    [eponai.client.routes :as routes]
    [eponai.common.routes :as common.routes]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.product :as product]
    [eponai.common.ui.product-filters :as pf]
    [eponai.common.ui.router :as router]
    [eponai.common.api.products :as products]
    [clojure.string :as str]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug error]]
    [eponai.web.ui.button :as button]
    [eponai.common.ui.search-bar :as search-bar]
    [clojure.string :as string]
    [eponai.common.browse :as browse]
    [eponai.common.database :as db]
    [eponai.web.ui.pagination :as pagination]
    [eponai.web.ui.content-item :as ci]
    [eponai.common.analytics.google :as ga]))

;(def sorting-vals
;  {:sort/name-inc  {:key [:store.item/name :store.item/price] :reverse? false}
;   :sort/name-dec  {:key [:store.item/name :store.item/price] :reverse? true}
;   :sort/price-inc {:key [:store.item/price :store.item/name] :reverse? false}
;   :sort/price-dec {:key [:store.item/price :store.item/name] :reverse? true}})

;(def sorting-vals
;  {:newest        {}
;   :lowest-price  {:key [:store.item/price :store.item/name ] :comp #(compare %1 %2) :label "Price (low to high)"}
;   :highest-price {:key [:store.item/price :store.item/name] :comp #(compare %2 %1) :label "Price (high to low)"}
;   :relevance     {}})

(defn fetch-item-data! [component browse-result page-range route-params]
  (om/transact!
    (om/get-reconciler component)
    `[({:query/browse-product-items ~(product/product-query)}
        {:product-items
         ~(into []
                (browse/page-items
                  (db/to-db component)
                  browse-result
                  (select-keys route-params [:top-category
                                             :sub-category
                                             :sub-sub-category])
                  page-range))})]))

(defn category-count [component category count-by-category]
  (if-let [id (:db/id category)]
    (get count-by-category id)
    (let [db (db/to-db component)]
      (some->> (:category/children category)
               (sequence
                 (comp (map #(db/entity db (:db/id %)))
                       (mapcat :category/children)
                       (map #(db/entity db (:db/id %)))
                       ;; This filter finds the "real" :db/id for this
                       ;; category within it's child category
                       ;; (which really is it's parent).
                       ;; Women -> Clothing -> <Women entity>
                       (filter (fn [child-child-cat]
                                 (= (:category/name child-child-cat)
                                    (:category/name category))))
                       (map #(get count-by-category (:db/id %)))
                       (filter some?)))
               (seq)
               (reduce + 0)))))

(defn category-nav-link [component category]
  (let [{:keys [query-params route-params] :as next-route}
        (merge-with merge (-> (routes/current-route component)
                              (dissoc :route)
                              (update :query-params dissoc :page-num))
                    (:category/route-map category))

        browse-result (get-in (om/props component) [:query/browse-products-2 :browse-result])
        page-range (browse/query-params->page-range query-params)
        searching? (some? (:search query-params))]
    {:href    (routes/map->url next-route)
     :onClick #(when searching?
                 (fetch-item-data! component browse-result page-range route-params))}))

(defn- vertical-category-menu [component children current-category label-fn count-by-category]
  (menu/vertical
    (css/add-class :nested)
    (->> children
         (filter (if (seq count-by-category)
                   (comp some? #(category-count component % count-by-category))
                   identity))
         (sort-by :category/name)
         (map-indexed
           (fn [i {:category/keys [path] :as category}]
             (menu/item
               (when (and (some? current-category)
                          (= path (:category/path current-category)))
                 (css/add-class ::css/is-active))
               (dom/a (category-nav-link component category)
                      (dom/span nil (label-fn category)))))))))

(defn selected-navigation [component]
  (let [{:query/keys [current-route navigation]} (om/props component)
        {:keys [route route-params]} current-route
        {:keys [top-category sub-category sub-sub-category]} route-params
        selected-names (if (= :browse/gender (common.routes/normalize-browse-route route))
                         [sub-category top-category sub-sub-category]
                         [top-category sub-category sub-sub-category])
        selected-nav-path (fn self [categories [n & names]]
                            (when n
                              (some (fn [[i category]]
                                      (when (= n (:category/name category))
                                        (if-let [next-find (self (:category/children category)
                                                                 names)]
                                          (cons i (cons :category/children next-find))
                                          (cons i nil))))
                                    (map-indexed vector categories))))]
    (vec (selected-nav-path navigation selected-names))))

(defn category-seq [component]
  (let [{:query/keys [navigation]} (om/props component)
        [top-idx & paths] (selected-navigation component)]
    (when-let [top-cat (get navigation top-idx)]
      (reductions get-in top-cat (partition 2 paths)))))

(defn send-product-analytics [products]
  (ga/send-product-list-impressions "Browse goods" products))

(defui Goods
  static om/IQuery
  (query [_]
    [{:query/browse-products-2 [{:browse-result/items (om/get-query ci/ProductItem)}
                                :browse-result/meta]}
     {:query/navigation [:db/id :category/name :category/label :category/path :category/route-map]}
     {:proxy/product-filters (om/get-query pf/ProductFilters)}
     ;{:query/countries [:country/code :country/name]}
     ;:query/locations
     :query/current-route])
  Object
  (select-shipping-destination [this country-code]
    (let [{:query/keys [current-route]} (om/props this)
          {:keys [route route-params query-params]} current-route
          new-query (if (= "anywhere" country-code)
                      (dissoc query-params :ship_to)
                      (assoc query-params :ship_to country-code))]
      (routes/set-url! this route route-params new-query)))
  (componentDidMount [this]
    (let [{:query/keys [browse-products-2]} (om/props this)
          products (:browse-result/items browse-products-2)]
      (send-product-analytics products)))
  (componentDidUpdate [this prev-props prev-state]
    (let [old-products (:browse-result/items (:query/browse-products-2 prev-props))
          new-products (:browse-result/items (:query/browse-products-2 (om/props this)))]
      (debug "component did update: " prev-props)
      (when-not (= old-products new-products)
        (send-product-analytics new-products))))

  (initLocalState [_]
    {:filters-open? false})
  (render [this]
    (let [{:proxy/keys [product-filters]
           :query/keys [browse-products-2 navigation current-route countries]} (om/props this)
          {:keys [filters-open?]} (om/get-state this)
          [top-category sub-category :as categories] (category-seq this)
          {:keys [route route-params query-params]} current-route
          items (:browse-result/items browse-products-2)
          browse-result (:browse-result/meta browse-products-2)
          {:browse-result/keys [count-by-category]} browse-result
          category-label-fn (fn [category]
                              (str (products/category-display-name category)
                                   ;; TODO: Make count corrent and show it here.
                                   ;(when-let [matches (category-count this category count-by-category)]
                                   ;  (str " " matches))
                                   ))
          page-range (browse/query-params->page-range query-params)
          pages (browse/pages browse-result)
          searching? (contains? query-params :search)]
      (debug "BAM")

      (dom/div
        {:id "sulo-items" :classes ["sulo-browse"]}
        ;(common/city-banner this locations)
        (when filters-open?
          (dom/div
            {:id "sl-product-filters"}
            (common/modal {:on-close #(om/update-state! this assoc :filters-open? false)
                           :size     "full"}
                          (pf/->ProductFilters (om/computed product-filters
                                                            {:on-click #(om/update-state! this assoc :filters-open? false)})))))
        ;(grid/row
        ;  nil
        ;  (grid/column
        ;    nil
        ;    ))

        (grid/row
          (->> (css/add-class :section)
               (css/align :center))
          (grid/column
            (->> (grid/column-size {:large 3})
                 (css/add-class :navigation)
                 (css/show-for :large))

            (menu/vertical
              (css/add-class :sl-navigation-parent)
              (->> navigation
                   (filter (if searching?
                             (comp some? #(category-count this % count-by-category))
                             identity))
                   (map (fn [category]
                          (let [is-active? (or (= (:category/name category) (:category/name (first categories))))]
                            (menu/item
                              (when is-active? (css/add-class :is-active))
                              (dom/a (category-nav-link this category)
                                     (dom/span nil (category-label-fn category)))
                              (vertical-category-menu this
                                                      (:category/children category)
                                                      (last categories)
                                                      category-label-fn
                                                      (when searching? count-by-category))))))))
            ;(dom/div
            ;  nil
            ;  (dom/label nil "Ship to")
            ;  (dom/select {:defaultValue "anywhere"
            ;               :onChange     #(.select-shipping-destination this (.-value (.-target %)))}
            ;              (dom/option {:value "anywhere"} "Anywhere")
            ;              (dom/optgroup
            ;                {:label "---"}
            ;                (map (fn [c]
            ;                       (dom/option {:value (:country/code c)} (:country/name c)))
            ;                     (sort-by :country/name countries)))))
            ;(dom/h1 nil (.toUpperCase (or (get-in current-route [:query-params :category]) "")))

            ;(if (nil? top-category)
            ;(menu/vertical
            ;  nil
            ;  (->> navigation
            ;       (sort-by :category/name)
            ;       (map (fn [category]
            ;              (menu/item
            ;                nil
            ;                (dom/a {:href (:category/href category)}
            ;                       (dom/span nil (products/category-display-name category))))))))
            ;  (menu/vertical
            ;    nil
            ;    (menu/item
            ;      nil
            ;      (dom/a {:href (:category/href top-category)}
            ;             (dom/strong nil (products/category-display-name top-category)))
            ;      (if (some? sub-category)
            ;        (menu/vertical
            ;          nil
            ;          (menu/item
            ;            nil
            ;            (dom/a {:href (:category/href sub-category)}
            ;                   (dom/strong nil (products/category-display-name sub-category)))
            ;            (vertical-category-menu (:category/children sub-category) (last categories))))
            ;        (vertical-category-menu (:category/children top-category) (last categories))))))
            )
          (grid/column
            (grid/column-size {:small 12 :large 9})

            (dom/div
              (css/add-class :section-title {:id "browse.products.title"})
              (dom/h2 nil
                      (str/upper-case
                        (cond (not-empty (:search query-params))
                              (str "Result for \"" (:search query-params) "\"")
                              (some? top-category)
                              (string/join " - " (filter some? [(products/category-display-name top-category)
                                                                (products/category-display-name sub-category)]))
                              (some? (-> current-route :route-params :top-category))
                              (string/replace (-> current-route :route-params :top-category) "-" " ")

                              (some? (-> current-route :route-params :sub-category))
                              (string/replace (-> current-route :route-params :sub-category) "-" " ")
                              :else
                              "All products"))))

            (dom/div
              (css/hide-for :large)
              (button/button
                (->> {:onClick #(om/update-state! this assoc :filters-open? true)
                      :classes [:sulo-dark]}
                     (button/hollow)
                     (button/expanded))
                (dom/span nil "Filter products")))
            (dom/div
              (css/add-class :sulo-items-container)
              (grid/row
                (->> (css/align :bottom)
                     ;(css/show-for :large)
                     )
                (grid/column
                  nil
                  (dom/div nil
                           (dom/small nil
                                      (dom/strong nil "FOUND ")
                                      (dom/span nil (str (count (:browse-result/items-in-category browse-result)) " items"))
                                      (when (pos? (count items))
                                        (dom/span nil (str " - Showing items "
                                                           (let [{:keys [page-size page-num]} page-range
                                                                 page-start (* (browse/zero-indexed-page-num page-num)
                                                                               page-size)]
                                                             (str (inc page-start)
                                                                  " to "
                                                                  (+ page-start (count items))
                                                                  " "))))))))

                (grid/column
                  (->> (grid/column-size {:large 4})
                       (css/add-class :sort)
                       (css/show-for :large))
                  (dom/label nil (dom/small nil "Sort"))
                  (dom/select
                    {:value    (or (:order query-params) (browse/default-order query-params))
                     :onChange #(routes/set-url! this route route-params (-> query-params
                                                                             (assoc :order (.-value (.-target %)))
                                                                             (dissoc :page-num)))}
                    (map (fn [k]
                           (dom/option {:value k}
                                       (browse/order-label k)))
                         (browse/order-values query-params)))))

              (grid/row
                (->> (grid/columns-in-row {:small 2 :medium 3 :large 4})
                     (css/add-class :product-grid))
                (map
                  (fn [p]
                    (grid/column
                      nil
                      (ci/->ProductItem (om/computed p
                                                     {:current-route current-route}))))
                  items))
              (when (< 1 (count pages))
                (dom/div
                  (css/add-class :section-footer)
                  (pagination/->Pagination
                    {:current-page (:page-num page-range)
                     :pages        (browse/pages browse-result)
                     :page->anchor-opts
                                   (fn [page]
                                     {:href
                                      (routes/map->url (routes/merge-route this {:query-params {:page-num page}}))
                                      ;; When not clicking the active page, fetch data for clicked page.
                                      :onClick
                                      #(do
                                        (fetch-item-data! this
                                                          browse-result
                                                          (assoc page-range :page-num page)
                                                          route-params)

                                        ;; Scroll to the top somewhere.
                                        ;; Choosing sulo-search-bar because it looked good.
                                        #?(:cljs
                                           (let [el (.getElementById js/document "sulo-search-bar")]
                                             (if (.-scrollIntoView el)
                                               (.scrollIntoView el)
                                               (.scrollTo js/window 0 0)))))})}))))))))))

(def ->Goods (om/factory Goods))

(router/register-component :browse Goods)