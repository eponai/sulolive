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
    [eponai.common.ui.elements.css :as css]))

(defui Goods
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/items (om/get-query product/Product)}
     :query/current-route])
  Object
  (render [this]
    (let [{:keys [proxy/navbar]
           :query/keys [current-route items]} (om/props this)]
      ;#?(:cljs (debug "Got items to render: " (om/props this)))
      ;(debug "Current route: " current-route)
      (debug "Got props: " (om/props this))

      (common/page-container
        {:navbar navbar :id "sulo-items"}
        (dom/div #js {:id "sulo-items-container"}
          (apply my-dom/div
                 (->> (css/grid-row)
                      (css/grid-row-columns {:small 2 :medium 3 :large 4}))
                 (map-indexed
                   (fn [i p]
                     (my-dom/div
                       (css/grid-column {:key i})
                       (pi/->ProductItem {:product p})))
                   (sort-by :db/id items))))))))

(def ->Goods (om/factory Goods))