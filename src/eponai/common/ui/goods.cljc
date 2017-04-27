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
    [eponai.common.api.products :as products]
    [clojure.string :as str]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]))

(def sorting-vals
  {:sort/name-inc  {:key [:store.item/name :store.item/price] :reverse? false}
   :sort/name-dec  {:key [:store.item/name :store.item/price] :reverse? true}
   :sort/price-inc {:key [:store.item/price :store.item/name] :reverse? false}
   :sort/price-dec {:key [:store.item/price :store.item/name] :reverse? true}})

(defn nav-breadcrumbs [browse-nav]
  (let [items (into [] (comp (take-while #(= 1 (count (:category/children %))))
                             (map (comp first :category/children))
                             (map (fn [category]
                                    (menu/item nil (dom/a {:href (:category/route category)}
                                                          (products/category-display-name category))))))
                    (iterate (comp first :category/children)
                             {:category/children [browse-nav]}))]
    (menu/breadcrumbs
      (when-not (< 1 (count items))
        {:classes [:invisible]})
      items)))

(defn- vertical-category-menu [categories current-category]
  (menu/vertical
    (css/add-class :nested)
    (map-indexed
      (fn [i {:category/keys [path] :as category}]
        (menu/item
          (cond->> {:key i}
                   (= path (:category/path current-category))
                   (css/add-class ::css/is-active))
          (dom/a {:href (:category/route category)}
                 (dom/span nil (products/category-display-name category)))))
      categories)))

(defui Goods
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/browse-items (om/get-query product/Product)}
     {:query/browse-nav [:category/name :category/label :category/path :category/route]}
     {:query/browse-category [:category/path]}
     {:proxy/product-filters (om/get-query pf/ProductFilters)}
     :query/current-route])
  Object
  (initLocalState [_]
    {:sorting       (get sorting-vals :sort/price-inc)
     :filters-open? false})
  (render [this]
    (let [{:proxy/keys [navbar product-filters]
           :query/keys [browse-items browse-nav browse-category]} (om/props this)
          {:keys [sorting filters-open?]} (om/get-state this)
          items browse-items]

      (debug "Browse nav: " (:query/browse-nav (om/props this)))

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
            (nav-breadcrumbs browse-nav)
            (dom/h1 nil (str/upper-case
                          (if-let [label (products/category-display-name browse-nav)]
                            label
                            "All")))
            ))
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
            (if (not-empty browse-nav)
              (menu/vertical
                nil
                (menu/item
                  nil
                  (dom/a {:href (:category/route browse-nav)}
                         (dom/strong nil (products/category-display-name browse-nav)))
                  (if (= 1 (count (:category/children browse-nav)))
                    (let [[category] (:category/children browse-nav)]
                      (menu/vertical
                        nil
                        (menu/item
                          nil
                          (dom/a {:href (:category/route category)}
                                 (dom/strong nil (products/category-display-name category)))
                          (vertical-category-menu (:category/children category) browse-category))))
                    (vertical-category-menu (:category/children browse-nav) browse-category))))))
          (grid/column
            (grid/column-size {:small 12 :large 9})

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

              (let [sorted (sort-by (apply juxt (:key sorting)) items)
                    ordered-products (if (:reverse? sorting)
                                       (reverse sorted)
                                       sorted)]
                (grid/products ordered-products
                               (fn [p]
                                 (pi/->ProductItem {:product p})))))))))))

(def ->Goods (om/factory Goods))