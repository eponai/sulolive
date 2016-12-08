(ns eponai.common.ui.checkout
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defui Checkout
  static om/IQuery
  (query [_]
    [{:query/cart [:cart/items]}])
  Object
  (render [this]
    (let [{:keys [query/cart]} (om/props this)]
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
                            (dom/span #js {:className "price"} (:item/price i))))))
                    (:cart/items cart)))))))

(def ->Checkout (om/factory Checkout))