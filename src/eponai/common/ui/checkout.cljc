(ns eponai.common.ui.checkout
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.utils :as utils]
    [eponai.common.ui.navbar :as nav]))

(defui Checkout
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/cart [{:cart/items [:item/name
                                 :item/price
                                 :item/img-src]}]}])
  Object
  (render [this]
    (let [{:keys [query/cart proxy/navbar]} (om/props this)]
      (common/page-container
        {:navbar navbar}
        (dom/div #js {:className "row column checkout-container"}
          (apply dom/div nil
                 (map (fn [i]
                        (dom/div #js {:className "row collapse align-middle content-item"}
                          (dom/div #js {:className "columns small-2"}
                            (dom/div #js {:className "photo-container"}
                              (dom/div #js {:className "photo square thumbnail" :style #js {:backgroundImage (str "url(" (:item/img-src i) ")")}})))
                          (dom/div #js {:className "columns small-10"}
                            (dom/div #js {:className "content-item-title-section"}
                              (dom/span #js {:className "name"} (:item/name i)))
                            (dom/div #js {:className "content-item-subtitle-section"}
                              (dom/span #js {:className "price"}
                                        (utils/two-decimal-price (:item/price i)))))))
                      (:cart/items cart))))))))

(def ->Checkout (om/factory Checkout))