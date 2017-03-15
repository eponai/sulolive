(ns eponai.common.ui.goods
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.product-item :as pi]
    [eponai.client.routes :as routes]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.navbar :as nav]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.product :as product]
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.menu :as menu]
    [clojure.string :as s]))

(def sorting-vals
  {:sort/name-inc {:key :store.item/name :reverse? false}
   :sort/name-dec {:key :store.item/name :reverse? true}
   :sort/price-inc {:key :store.item/price :reverse? false}
   :sort/price-dec {:key :store.item/price :reverse? true}})

(defn breadcrumbs [category]
  (let [items (loop [c category
                     l '()]
                (if (some? c)
                  (let [parent (first (:category/_children c))]
                    (recur parent (conj l (menu/item nil (dom/a #js {:href (routes/url :products/categories {:category (:category/path c)})}
                                                                (:category/label c))))))
                  l))]
    (when (< 1 (count items))
      (dom/nav #js {:role "navigation"}
               (menu/breadcrumbs
                 nil
                 items)))))

(defui Goods
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/items (om/get-query product/Product)}
     '{:query/category [:category/label
                       :category/path
                       {:category/_children ...}
                        {:category/children ...}]}
     :query/current-route])
  Object
  (initLocalState [_]
    {:sorting {:key :store.item/name
               :reverse? false}})
  (render [this]
    (let [{:keys [proxy/navbar]
           :query/keys [current-route items category top-categories]} (om/props this)
          {:keys [sorting]} (om/get-state this)
          current-category (get-in current-route [:route-params :category] "")]

      (common/page-container
        {:navbar navbar :id "sulo-items" :class-name "sulo-browse"}
        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (css/grid-column)
            (breadcrumbs category)
            (dom/h1 nil (.toUpperCase (if (not-empty current-category)
                                        (:category/label category "")
                                        "All")))))
        (my-dom/div
          (->> (css/grid-row)
               (css/hide-for {:size :large}))
          (my-dom/div
            (css/grid-column)
            (dom/a #js {:className "button hollow expanded"} "Filter Products")))
        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (->> (css/grid-column)
                 (css/add-class :navigation)
                 (css/show-for {:size :large})
                 (css/grid-column-size {:large 3}))
            ;(dom/h1 nil (.toUpperCase (or (get-in current-route [:query-params :category]) "")))
            (menu/vertical
              nil
              (menu/item
                nil
                (dom/a nil (dom/strong nil (:category/label category)))
                (menu/vertical
                  (css/add-class :nested)
                  (map-indexed (fn [i c]
                                 (let [{:category/keys [label path]} c]
                                   (menu/item {:key i}
                                              (dom/a #js {:href (routes/url :products/categories {:category path})}
                                                     (dom/span nil label)))))
                               (:category/children category)))))
            ;(if (not-empty current-category)
            ;  (menu/vertical
            ;    nil
            ;    (menu/item nil (dom/a #js {:href    (routes/url :products/collections {:collection "women"})}
            ;                          (dom/strong nil "Women")))
            ;    (menu/item nil (dom/a #js {:href (routes/url :products/collections {:collection "men"})}
            ;                          (dom/strong nil "Men")))
            ;    (menu/item nil (dom/a #js {:href (routes/url :products/collections {:collection "kids"})}
            ;                          (dom/strong nil "Kids")))
            ;    (menu/item nil (dom/a #js {:href (routes/url :products/collections {:collection "home"})}
            ;                          (dom/strong nil "Home")))
            ;    (menu/item nil (dom/a #js {:href (routes/url :products/collections {:collection "art"})}
            ;                          (dom/strong nil "Art")))))
            )
          (my-dom/div
            (css/grid-column)
            (dom/div #js {:className "sulo-items-container"}
              (my-dom/div
                (->> (css/grid-row)
                     (css/align :bottom)
                     (css/show-for {:size :large}))
                (my-dom/div
                  (css/grid-column)
                  (dom/small nil (dom/strong nil "SHOWING ") (count items) " items")
                  ;(dom/h4 nil "Showing " (count items) " items")
                  )
                ;(my-dom/div
                ;  (->> (css/grid-column)
                ;       (css/text-align :right))
                ;  (dom/label nil "Sort"))
                (my-dom/div
                  (->> (css/grid-column)
                       (css/add-class :sort)
                       (css/grid-column-size {:large 4}))
                  (dom/label nil (dom/small nil "Sort"))
                  (dom/select #js {:defaultValue (name :sort/name-inc)
                                   :onChange     #(om/update-state! this assoc :sorting (get sorting-vals (keyword "sort" (.. % -target -value))))}
                              ;(dom/option #js {:value (name :sort/name-inc)} "Alphabetical (ascending)")
                              ;(dom/option #js {:value (name :sort/name-dec)} "Alphabetical (descending)")
                              (dom/option #js {:value (name :sort/price-inc)} "Price (low to high)")
                              (dom/option #js {:value (name :sort/price-dec)} "Price (high to low)"))))
              (my-dom/div
                (->> (css/grid-row)
                     (css/grid-row-columns {:small 2 :medium 3}))
                (map-indexed
                  (fn [i p]
                    (my-dom/div
                      (css/grid-column {:key i})
                      (pi/->ProductItem {:product p})))
                  (let [sorted (sort-by (:key sorting) items)]
                    (if (:reverse? sorting)
                      (reverse sorted)
                      sorted)))))))))))

(def ->Goods (om/factory Goods))