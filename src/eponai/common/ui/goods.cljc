(ns eponai.common.ui.goods
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.product-item :as pi]
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

(defui Goods
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/items (om/get-query product/Product)}
     :query/current-route])
  Object
  (initLocalState [_]
    {:sorting {:key :store.item/name
               :reverse? false}})
  (render [this]
    (let [{:keys [proxy/navbar]
           :query/keys [current-route items]} (om/props this)
          {:keys [sorting]} (om/get-state this)
          current-category (get-in current-route [:route-params :collection] "")]

      (common/page-container
        {:navbar navbar :id "sulo-items" :class-name "sulo-browse"}
        (dom/div #js {:id "sulo-items-container"}
          (my-dom/div
            (css/grid-row)
            (my-dom/div
              (css/grid-column)
              (dom/h1 nil (.toUpperCase current-category))))
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
                (menu/item nil
                           (dom/a nil (dom/strong nil (s/capitalize current-category)))
                           (menu/vertical
                             {:classes [:nested]}
                             (menu/item nil (dom/a nil "Accessories"))
                             (menu/item nil (dom/a nil "Clothing"))
                             (menu/item nil (dom/a nil "Jewelry"))
                             (menu/item nil (dom/a nil "Shoes"))))))
            (my-dom/div
              (css/grid-column)
              (my-dom/div
                (->> (css/grid-row)
                     (css/align :middle)
                     (css/show-for {:size :large}))
                (my-dom/div
                  (css/grid-column)
                  ;(dom/h4 nil "Showing " (count items) " items")
                  )
                (my-dom/div
                  (->> (css/grid-column)
                       (css/text-align :right))
                  (dom/label nil "Sort"))
                (my-dom/div
                  (css/grid-column)
                  (dom/div nil
                    (dom/select #js {:defaultValue (name :sort/name-inc)
                                     :onChange #(om/update-state! this assoc :sorting (get sorting-vals (keyword "sort" (.. % -target -value))))}
                                (dom/option #js {:value (name :sort/name-inc)} "Alphabetical (ascending)")
                                (dom/option #js {:value (name :sort/name-dec)} "Alphabetical (descending)")
                                (dom/option #js {:value (name :sort/price-inc)} "Price (low to high)")
                                (dom/option #js {:value (name :sort/price-dec)} "Price (high to low)")))))
              (apply my-dom/div
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