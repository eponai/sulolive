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
   :sort/name-dec {:key :store.item/name :reverse? true}})

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
      ;#?(:cljs (debug "Got items to render: " (om/props this)))
      ;(debug "Current route: " current-route)
      ;(debug "Got props: " (om/props this))

      (common/page-container
        {:navbar navbar :id "sulo-items"}
        (dom/div #js {:id "sulo-items-container"}
          (my-dom/div
            (->> (css/grid-row)
                 ;(css/hide-for {:size :large})
                 )
            (my-dom/div
              (css/grid-column)
              (dom/h1 nil (.toUpperCase current-category))))
          (my-dom/div
            (css/grid-row)
            (my-dom/div
              (->> (css/grid-column)
                   (css/add-class :navigation)
                   (css/show-for {:size :large})
                   (css/grid-column-size {:small 0 :medium 3 :large 3}))
              ;(dom/h1 nil (.toUpperCase (or (get-in current-route [:query-params :category]) "")))
              (menu/vertical
                nil
                (menu/item nil (dom/a nil (dom/strong nil (s/capitalize current-category)))
                           (menu/vertical {:classes [:nested]}
                                          (menu/item nil (dom/a nil "Accessories"))
                                          (menu/item nil (dom/a nil "Clothing"))
                                          (menu/item nil (dom/a nil "Jewelry"))
                                          (menu/item nil (dom/a nil "Shoes"))))))
            (my-dom/div
              (css/grid-column)
              (my-dom/div
                (->> (css/grid-row)
                     (css/hide-for {:size :large}))
                (my-dom/div
                  (css/grid-column)
                  (dom/a #js {:className "button hollow expanded"} "Filter Products")))
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
                    (dom/select #js {:defaultValue "a-z"
                                     :onChange #(om/update-state! this assoc :sorting (get sorting-vals (keyword "sort" (.. % -target -value))))}
                                (dom/option #js {:value (name :sort/name-inc)} "A-Z")
                                (dom/option #js {:value (name :sort/name-dec)} "Z-A")))))
              (apply my-dom/div
                     (->> (css/grid-row)
                          (css/grid-row-columns {:small 2 :medium 3}))
                     (map-indexed
                       (fn [i p]
                         (my-dom/div
                           (css/grid-column {:key i})
                           (pi/->ProductItem {:product p})))
                       (let [sorted (sort-by :store.item/name items)]
                         (debug "Sorted items: " sorted)
                         (if (:reverse? sorting)
                           (reverse sorted)
                           sorted)))))))))))

(def ->Goods (om/factory Goods))