(ns eponai.common.ui.product-item
  (:require
    [eponai.common :as com]
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
          on-click #(om/update-state! this assoc :show-item? true)]

      (debug "Breakpoint: " breakpoint)
      (dom/div #js {:className "column content-item product-item"}
        (dom/a #js {:className "photo-container"
                    :onClick   (when-not open-url? on-click)
                    :href      (when (or open-url? (nil? on-click)) (str "/goods/" (:item/id product)))}
               (dom/div #js {:className "photo square" :style #js {:backgroundImage (str "url(" (:item/img-src product) ")")}}))
        (dom/div #js {:className "content-item-title-section"}
          (dom/a nil (:item/name product)))
        (dom/div #js {:className "content-item-subtitle-section"}
          (dom/strong nil (com/format-str "$%.2f" (:item/price product)))
          (common/rating-element 5 11))

        (when show-item?
          (common/modal {:on-close #(om/update-state! this assoc :show-item? false)
                  :size :large}
                 (product/->Product product)))))))

(def ->ProductItem (om/factory ProductItem))