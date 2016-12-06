(ns eponai.common.ui.product
  (:require
    [eponai.common.ui.common :as c]
    [om.next :as om :refer [defui]]
    [om.dom :as dom]))

(defui Product
  static om/IQuery
  (query [_]
    [:item/name :item/price :item/img-src :item/store])
  Object
  (render [this]
    (let [{:keys     [item/price item/store item/img-src]
           item-name :item/name} (om/props this)]
      (dom/div
        #js {:id "sulo-product"}
        (dom/div #js {:className "row content-items-container store-container align-middle"}
          (dom/div #js {:className "columns small-4 medium-2"}
            (dom/div #js {:className "content-item-thumbnail-container"}
              (dom/div #js {:className "content-item-thumbnail" :style #js {:backgroundImage (str "url(" (:store/photo store) ")")}})))
          (dom/div #js {:className "columns"}
            (dom/a #js {:href (str "/store/" (:store/id store))}
                   (dom/h1 #js {:className "store-name"} (:store/name store)))
            (c/rating-element (:store/rating store) (:store/review-count store))))


        (dom/div #js {:className "row product-container"}
          (dom/div #js {:className "column small-12 medium-8 small-order-2 medium-order-1"}
            (dom/div #js {:className "product-photo-container row"}
              (dom/div #js {:className "product-photo" :style #js {:backgroundImage (str "url(" img-src ")")}}))
            (apply dom/div #js {:className "row small-up-4 medium-up-6"}
                   (map (fn [im]
                          (dom/div #js {:className "column"}
                            (dom/div #js {:className "content-item-thumbnail" :style #js {:backgroundImage (str "url(" im ")")}})))
                        (take 4 (repeat img-src)))))
          (dom/div #js {:className "column product-info-container small-order-1 medium-order-2"}
            (dom/div #js {:className "product-info"}
              (dom/h2 #js {:className "product-info-title"} item-name)
              (dom/h3 #js {:className "product-info-price"}
                      price))
            (dom/div #js {:className "product-action-container clearfix"}
              (dom/a #js {:className "button expanded"} "Add to Cart"))))))))

(def ->Product (om/factory Product))
