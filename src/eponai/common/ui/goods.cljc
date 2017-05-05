(ns eponai.common.ui.goods
  (:require
    [eponai.client.routes :as routes]
    [eponai.common.routes :as common.routes]
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
    [eponai.common.ui.router :as router]
    [eponai.common.api.products :as products]
    [clojure.string :as str]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]))

(def sorting-vals
  {:sort/name-inc  {:key [:store.item/name :store.item/price] :reverse? false}
   :sort/name-dec  {:key [:store.item/name :store.item/price] :reverse? true}
   :sort/price-inc {:key [:store.item/price :store.item/name] :reverse? false}
   :sort/price-dec {:key [:store.item/price :store.item/name] :reverse? true}})

(defn nav-breadcrumbs [categories]
  (let [items (into [(menu/item nil (dom/a {:href (routes/url :browse/all-items)}
                                           "All"))]
                    (map (fn [category]
                           (menu/item nil (dom/a {:href (:category/href category)}
                                                 (products/category-display-name category)))))
                    categories)]
    (menu/breadcrumbs
      (when-not (< 1 (count items))
        {:classes [:invisible]})
      items)))

(defn- vertical-category-menu [children current-category]
  (menu/vertical
    (css/add-class :nested)
    (->> children
         (sort-by :category/name)
         (map-indexed
           (fn [i {:category/keys [path] :as category}]
             (menu/item
               (cond->> {:key i}
                        (and (some? current-category)
                             (= path (:category/path current-category)))
                        (css/add-class ::css/is-active))
               (dom/a {:href (:category/href category)}
                      (dom/span nil (products/category-display-name category)))))))))

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
     {:query/browse-items (om/get-query product/Product)}
     {:query/navigation [:category/name :category/label :category/path :category/href]}
     {:proxy/product-filters (om/get-query pf/ProductFilters)}
     :query/current-route])
  Object
  (initLocalState [_]
    {:sorting       (get sorting-vals :sort/price-inc)
     :filters-open? false})
  (render [this]
    (let [{:proxy/keys [navbar product-filters]
           :query/keys [browse-items navigation selected-navigation]} (om/props this)
          {:keys [sorting filters-open?]} (om/get-state this)
          [top-category sub-category :as categories] (category-seq this)
          items browse-items]

      (common/page-container
        {:navbar navbar :id "sulo-items" :class-name "sulo-browse"}
        (when filters-open?
          (dom/div
            {:id "sl-product-filters"}
            (common/modal {:on-close #(om/update-state! this assoc :filters-open? false)
                           :size     "full"}
                          (pf/->ProductFilters (om/computed product-filters
                                                            {:on-click #(om/update-state! this assoc :filters-open? false)})))))
        (grid/row
          nil
          (grid/column
            nil
            (nav-breadcrumbs (category-seq this))
            (dom/h1 nil (str/upper-case
                          (if (some? top-category)
                            (products/category-display-name top-category)
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
            (if (nil? top-category)
              (menu/vertical
                nil
                (->> navigation
                     (sort-by :category/name)
                     (map (fn [category]
                            (menu/item
                              nil
                              (dom/a {:href (:category/href category)}
                                     (dom/span nil (products/category-display-name category))))))))
              (menu/vertical
                nil
                (menu/item
                  nil
                  (dom/a {:href (:category/href top-category)}
                         (dom/strong nil (products/category-display-name top-category)))
                  (if (some? sub-category)
                    (menu/vertical
                      nil
                      (menu/item
                        nil
                        (dom/a {:href (:category/href sub-category)}
                               (dom/strong nil (products/category-display-name sub-category)))
                        (vertical-category-menu (:category/children sub-category) (last categories))))
                    (vertical-category-menu (:category/children top-category) (last categories)))))))
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

(defmethod router/route->component :browse [_] {:component Goods})