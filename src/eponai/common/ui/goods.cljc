(ns eponai.common.ui.goods
  (:require
    [eponai.client.routes :as routes]
    [eponai.common.routes :as common.routes]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.product :as product]
    [eponai.common.ui.product-filters :as pf]
    [eponai.common.ui.product-item :as pi]
    [eponai.common.ui.router :as router]
    [eponai.common.api.products :as products]
    [clojure.string :as str]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug error]]
    [eponai.web.ui.button :as button]
    [eponai.common.ui.search-bar :as search-bar]
    [eponai.web.ui.footer :as foot]
    [clojure.string :as string]
    [eponai.common.browse :as browse]
    [eponai.common.database :as db]))

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

(defn- vertical-category-menu [component children current-category label-fn]
  (menu/vertical
    (css/add-class :nested)
    (->> children
         (sort-by :category/name)
         (map-indexed
           (fn [i {:category/keys [path] :as category}]
             (menu/item
               (when (and (some? current-category)
                          (= path (:category/path current-category)))
                 (css/add-class ::css/is-active))
               (dom/a {:href (routes/map->url (routes/merge-route component (:category/route-map category)))}
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

(defui Goods
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:proxy/footer (om/get-query foot/Footer)}
     {:query/browse-products-2 (om/get-query product/Product)}
     {:query/navigation [:db/id :category/name :category/label :category/path :category/route-map]}
     {:proxy/product-filters (om/get-query pf/ProductFilters)}
     {:query/countries [:country/code :country/name]}
     :query/locations
     :query/current-route])
  Object
  (select-shipping-destination [this country-code]
    (let [{:query/keys [current-route]} (om/props this)
          {:keys [route route-params query-params]} current-route
          new-query (if (= "anywhere" country-code)
                      (dissoc query-params :ship_to)
                      (assoc query-params :ship_to country-code))]
      (routes/set-url! this route route-params new-query)))
  (initLocalState [_]
    {:sorting       browse/default-order
     :filters-open? false})
  (render [this]
    (let [{:proxy/keys [navbar product-filters footer]
           :query/keys [browse-products-2 navigation locations current-route countries]} (om/props this)
          {:keys [filters-open?]} (om/get-state this)
          [top-category sub-category :as categories] (category-seq this)
          {:keys [route route-params query-params]} current-route
          {:keys [items browse-result]} browse-products-2
          {:browse-result/keys [count-by-category]} browse-result
          category-label-fn (fn [category]
                              (str (products/category-display-name category)
                                   (when-let [matches (category-count this category count-by-category)]
                                     (str " " matches))))]

      (debug " items: " items)
      (debug " navigation: " navigation)
      (common/page-container
        {:navbar navbar :id "sulo-items" :class-name "sulo-browse" :footer footer}
        (common/city-banner this locations)
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
          (css/add-class :section)
          (grid/column
            (->> (grid/column-size {:large 3})
                 (css/add-class :navigation)
                 (css/show-for :large))

            (menu/vertical
              (css/add-class :sl-navigation-parent)
              (->> navigation
                   (filter (if (seq count-by-category)
                             (comp some? #(category-count this % count-by-category))
                             identity))
                   (map (fn [category]
                          (let [is-active? (= (:category/name category) (:category/name (first categories)))]
                            (menu/item
                              (when is-active? (css/add-class :is-active))
                              (dom/a {:href (routes/map->url (routes/merge-route this (:category/route-map category)))}
                                     (dom/span nil (category-label-fn category)))
                              (vertical-category-menu this
                                                      (:category/children category)
                                                      (last categories)
                                                      category-label-fn)))))))
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
              (css/add-class :section-title)
              (dom/h2 nil (str/upper-case
                            (cond (some? top-category)
                                  (string/join " - " (remove nil? [(products/category-display-name top-category) (products/category-display-name sub-category)]))
                                  (not-empty (:search query-params))
                                  (str "Result for \"" (:search query-params) "\"")
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
                  (dom/small nil
                             (dom/strong nil "FOUND ")
                             (dom/span nil (str (count (:browse-result/items browse-result)) " items"))))

                (grid/column
                  (->> (grid/column-size {:large 4})
                       (css/add-class :sort)
                       (css/show-for :large))
                  (dom/label nil (dom/small nil "Sort"))
                  (dom/select
                    {:defaultValue (or (:order query-params) (browse/default-order query-params))
                     :onChange     #(routes/set-url! this route route-params (assoc query-params :order (.-value (.-target %))))}
                    (map (fn [k]
                           (dom/option {:value k}
                                       (browse/order-label k)))
                         (browse/order-values query-params)))))

              (grid/products items
                             (fn [p]
                               (pi/->ProductItem {:product p}))))))))))

(def ->Goods (om/factory Goods))

(router/register-component :browse Goods)