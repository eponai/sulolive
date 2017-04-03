(ns eponai.common.ui.goods
  (:require
    [eponai.client.routes :as routes]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.product :as product]
    [eponai.common.ui.product-filters :as pf]
    [eponai.common.ui.product-item :as pi]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]))

(def sorting-vals
  {:sort/name-inc  {:key :store.item/name :reverse? false}
   :sort/name-dec  {:key :store.item/name :reverse? true}
   :sort/price-inc {:key :store.item/price :reverse? false}
   :sort/price-dec {:key :store.item/price :reverse? true}})

(defn breadcrumbs [category]
  (let [items (loop [c category
                     l '()]
                (if (some? c)
                  (let [{:category/keys [_children]} c
                        parent (if (map? _children) _children (first _children))]
                    (recur parent
                           (conj l (menu/item nil (dom/a {:href (routes/url :products/categories {:category (:category/path c)})}
                                                         (:category/label c))))))
                  l))]
    (menu/breadcrumbs
      (when-not (< 1 (count items))
        {:classes [:invisible]})
      items)))

(defn category-parent [c]
  (let [{:category/keys [_children]} c]
    (if (map? _children)
      _children
      (first _children))))

(defui Goods
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/items (om/get-query product/Product)}
     '{:query/category [:category/label {:category/photo [:photo/path]} :category/path
                        {:category/_children ...}
                        {:category/children ...}]}
     {:query/top-categories [:category/label :category/path]}
     {:proxy/product-filters (om/get-query pf/ProductFilters)}
     :query/current-route])
  Object
  (initLocalState [_]
    {:sorting {:key      :store.item/name
               :reverse? false}
     :filters-open? false})
  (render [this]
    (let [{:proxy/keys       [navbar product-filters]
           :query/keys [current-route items category top-categories]} (om/props this)
          {:keys [sorting filters-open?]} (om/get-state this)
          current-category (get-in current-route [:route-params :category] "")
          parent (category-parent category)]
      
      (common/page-container
        {:navbar navbar :id "sulo-items" :class-name "sulo-browse"}
        (when filters-open?
          (dom/div
            {:id "sl-product-filters"}
            (common/modal {:on-close #(om/update-state! this assoc :filters-open? false)
                           :size     "full"}
                          (pf/->ProductFilters (om/computed product-filters
                                                            {:on-change #(om/update-state! this assoc :filters-open? false)})))))
        (grid/row
          nil
          (grid/column
            nil
            (breadcrumbs category)
            (dom/h1 nil (.toUpperCase (if (not-empty current-category)
                                        (:category/label category "")
                                        "All")))))
        (grid/row
          (css/hide-for :large)
          (grid/column
            nil
            (dom/a
              (->> {:onClick #(om/update-state! this assoc :filters-open? true)}
                   (css/button-hollow)
                   (css/add-class :expanded))
              (dom/span nil "Filter Products"))))
        (grid/row
          nil
          (grid/column
            (->> (grid/column-size {:large 3})
                 (css/add-class :navigation)
                 (css/show-for :large))
            ;(dom/h1 nil (.toUpperCase (or (get-in current-route [:query-params :category]) "")))
            (if (not-empty current-category)
              (menu/vertical
                nil
                (menu/item
                  nil
                  (dom/a {:href (routes/url :products/categories {:category (if parent
                                                                                 (:category/path parent)
                                                                                 (:category/path category))})}
                         (dom/strong nil (if parent
                                                 (:category/label parent)
                                                 (:category/label category))))
                  (menu/vertical
                    (css/add-class :nested)
                    (map-indexed
                      (fn [i c]
                        (let [{:category/keys [label path]} c]
                          (menu/item
                            (cond->> {:key i}
                                     (= path current-category)
                                     (css/add-class ::css/is-active))
                            (dom/a {:href (routes/url :products/categories {:category path})}
                                   (dom/span nil label)))))
                      (if parent
                        (:category/children parent)
                        (:category/children category))))))
              (menu/vertical
                nil
                (map-indexed
                  (fn [i c]
                    (let [{:category/keys [label path]} c]
                      (menu/item
                        (cond->> {:key i}
                                 (= path current-category)
                                 (css/add-class ::css/is-active))
                        (dom/a {:href (routes/url :products/categories {:category path})}
                               (dom/strong nil label)))))
                  top-categories))))
          (grid/column
            (grid/column-size {:small 12 :large 9})
            (when (not-empty (map :category/photo (:category/children category)))
              (dom/div
                (css/add-class :categories)
                (grid/row
                  nil
                  (grid/column
                    nil
                    (dom/strong nil "Categories")))

                (grid/row
                  (grid/columns-in-row {:small 2 :medium 3})
                  (map
                    (fn [c]
                      (let [{:category/keys [photo label path]} c]
                        (grid/column
                          (css/add-class :expand)
                          (dom/a
                            (->> {:href (routes/url :products/categories {:category path})}
                                 (css/add-class :content-item)
                                 (css/add-class :collection-item))
                            (photo/with-overlay
                              nil
                              (photo/photo {:src (:photo/path photo)})
                              (dom/div
                                (->> (css/text-align :center))
                                (dom/span nil label)))))))
                    (filter #(some? (:category/photo %)) (:category/children category))))))

            (dom/div
              (css/add-class :sulo-items-container)
              (grid/row
                (->> (css/align :bottom)
                     (css/show-for :large))
                (grid/column
                  nil
                  (dom/small nil
                             (dom/strong nil "SHOWING ")
                             (dom/span nil (str (count items) " items"))))

                (grid/column
                  (->> (grid/column-size {:large 4})
                       (css/add-class :sort))
                  (dom/label nil (dom/small nil "Sort"))
                  (dom/select
                    {:defaultValue (name :sort/name-inc)
                     :onChange     #(om/update-state! this assoc :sorting (get sorting-vals (keyword "sort" (.. % -target -value))))}
                    ;(dom/option #js {:value (name :sort/name-inc)} "Alphabetical (ascending)")
                    ;(dom/option #js {:value (name :sort/name-dec)} "Alphabetical (descending)")
                    (dom/option {:value (name :sort/price-inc)} "Price (low to high)")
                    (dom/option {:value (name :sort/price-dec)} "Price (high to low)"))))

              (let [sorted (sort-by (:key sorting) items)
                    ordered-products (if (:reverse? sorting)
                                       (reverse sorted)
                                       sorted)]
                (grid/products ordered-products
                               (fn [p]
                                 (pi/->ProductItem {:product p})))))))))))

(def ->Goods (om/factory Goods))