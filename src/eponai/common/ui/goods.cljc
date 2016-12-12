(ns eponai.common.ui.goods
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.product-item :as pi]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.navbar :as nav]))

(defui Goods
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/all-items [:item/name
                        :item/price
                        :item/img-src
                        :item/id
                        {:item/store [:store/name :store/photo :store/rating :store/review-count]}]}])
  Object
  (render [this]
    (let [{:keys [query/all-items proxy/navbar]} (om/props this)]
      (common/page-container
        {:navbar navbar}
        (dom/div #js {:id "sulo-items-container"}
          (apply dom/div #js {:className "row small-up-2 medium-up-3 large-up-4"}
                 (map (fn [p]
                        (pi/->ProductItem {:product p})
                        ;(common/product-element p)
                        )
                      all-items)))))))

(def ->Goods (om/factory Goods))