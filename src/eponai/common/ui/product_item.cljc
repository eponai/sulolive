(ns eponai.common.ui.product-item
  (:require
    [eponai.common.ui.product :as product]
    [eponai.common.ui.common :as common]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    #?(:cljs
       [eponai.web.utils :as utils])
    [taoensso.timbre :refer [debug]]))

(defui ProductItem
  Object
  (initLocalState [this]
    {:resize-listener #(.on-window-resize this)
     #?@(:cljs [:breakpoint (utils/breakpoint js/window.innerWidth)])})
  #?(:cljs
     (on-window-resize [this]
                       (om/update-state! this assoc :breakpoint (utils/breakpoint js/window.innerWidth))))
  (componentDidMount [this]
    #?(:cljs (.addEventListener js/window "resize" (:resize-listener (om/get-state this)))))
  (componentWillUnmount [this]
    #?(:cljs (.removeEventListener js/window "resize" (:resize-listener (om/get-state this)))))

  (render [this]
    (let [{:keys [on-click product]} (om/props this)
          {:keys [show-item? breakpoint]} (om/get-state this)
          open-url? #?(:cljs (utils/bp-compare :large breakpoint >) :clj false)
          on-click (when-not open-url? #(om/update-state! this assoc :show-item? true))
          product-href (when (or open-url? (nil? on-click)) (str "/goods/" (:item/id product)))]

      ;; TODO: Very similar to eponai.common.ui.common/product-element
      ;;       Extract?
      (dom/div #js {:className "column content-item product-item"}
        (dom/a #js {:onClick   on-click
                    :href      product-href}
               (dom/div #js {:className "photo-container"}
                 (dom/div #js {:className "photo square" :style #js {:backgroundImage (str "url(" (:item/img-src product) ")")}})))
        (dom/div #js {:className "content-item-title-section"}
          (dom/a #js {:onClick on-click
                      :href    product-href}
                 (:item/name product)))
        (dom/div #js {:className "content-item-subtitle-section"}
          (dom/strong nil (common/two-decimal-price (:item/price product)))
          (common/rating-element 4 11))

        (when show-item?
          (common/modal {:on-close #(om/update-state! this assoc :show-item? false)
                  :size :large}
                 (product/->Product product)))))))

(def ->ProductItem (om/factory ProductItem))
