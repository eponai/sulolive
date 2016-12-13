(ns eponai.common.ui.goods
  (:require
    [cemerick.url :as url]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.product-item :as pi]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.navbar :as nav]))

;`({:query/store [:db/id
;                 :store/cover
;                 :store/photo
;                 {:item/_store ~(om/get-query item/Product)}
;                 {:stream/_store [:stream/name :stream/viewer-count]}
;                 :store/name
;                 :store/rating
;                 :store/review-count]} {:store-id ~'?store-id})
(defui Goods
  static om/IQueryParams
  (params [_]
    #?(:cljs
       (let [href js/window.location.href
             {:strs [category]} (:query (url/url href))]
         {:category category})))
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     `({:query/items [:db/id
                      :item/name
                      :item/price
                      :item/img-src
                      :item/id
                      :item/category
                      {:item/store [:store/name :store/photo :store/rating :store/review-count]}]} {:category ~'?category})])
  Object
  (render [this]
    (let [{:keys [query/items proxy/navbar]} (om/props this)]
      (common/page-container
        {:navbar navbar}
        (dom/div #js {:id "sulo-items-container"}
          (apply dom/div #js {:className "row small-up-2 medium-up-3 large-up-4"}
                 (map (fn [p]
                        (pi/->ProductItem {:product p})
                        ;(common/product-element p)
                        )
                      items)))))))

(def ->Goods (om/factory Goods))