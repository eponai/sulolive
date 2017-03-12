(ns eponai.common.ui.product-item
  (:require
    [eponai.common.ui.product :as product]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.utils :as ui-utils]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    #?(:cljs
       [eponai.web.utils :as utils])
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.client.routes :as routes]))

(defui ProductItem
  Object
  (initLocalState [this]
    #?(:cljs {:resize-listener #(.on-window-resize this)
              :breakpoint      (utils/breakpoint js/window.innerWidth)}))
  (on-window-resize [this]
    #?(:cljs (om/update-state! this assoc :breakpoint (utils/breakpoint js/window.innerWidth))))
  (componentDidMount [this]
    #?(:cljs (.addEventListener js/window "resize" (:resize-listener (om/get-state this)))))
  (componentWillUnmount [this]
    #?(:cljs (.removeEventListener js/window "resize" (:resize-listener (om/get-state this)))))

  (render [this]
    (let [{:keys [product]} (om/props this)
          {:keys [display-content]} (om/get-computed this)
          {:keys [show-item? breakpoint]} (om/get-state this)
          open-url? #?(:cljs (utils/bp-compare :large breakpoint >) :clj false)
          on-click (when-not open-url? #(om/update-state! this assoc :show-item? true))
          product-href (when (or open-url? (nil? on-click))
                         (routes/url :product {:product-id (:db/id product)}))]

      ;; TODO: Very similar to eponai.common.ui.common/product-element
      ;;       Extract?
      (common/product-element
        {:on-click on-click
         :href     product-href}
        product
        (when show-item?
          (common/modal {:on-close #(om/update-state! this assoc :show-item? false)
                         :size     :large}
                        (product/->Product product)))))))

(def ->ProductItem (om/factory ProductItem))
