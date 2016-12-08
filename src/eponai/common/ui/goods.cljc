(ns eponai.common.ui.goods
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.product-item :as pi]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui Goods
  static om/IQuery
  (query [_]
    [{:query/all-items [:item/name
                        :item/price
                        :item/img-src
                        :item/id
                        {:item/_store [:store/name :store/photo :store/rating :store/review-count]}]}])
  Object
  (render [this]
    (let [{:keys [query/all-items]} (om/props this)]
      (dom/div #js {:className "items"}
        (apply dom/div #js {:className "row small-up-2 medium-up-3 large-up-4"}
               (map (fn [p]
                      (pi/->ProductItem {:product p})
                      ;(common/product-element p)
                      )
                    all-items))))))

(def ->Goods (om/factory Goods))