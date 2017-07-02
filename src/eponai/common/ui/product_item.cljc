(ns eponai.common.ui.product-item
  (:require
    [eponai.common.ui.product :as product]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.utils :as ui-utils]
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    #?(:cljs
       [eponai.web.utils :as utils])
    [taoensso.timbre :refer [debug]]
    [eponai.client.routes :as routes]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.photo :as photo]
    [clojure.string :as string]
    [cemerick.url :as url]))

(defn product-element [opts product & children]
  (let [{:keys [on-click open-url?]} opts
        goods-href (when (or open-url? (nil? on-click)) (product/product-url product))
        on-click (when-not open-url? on-click)
        {:store.item/keys [photos price]
         item-name        :store.item/name
         store            :store/_items} product
        {:store.item.photo/keys [photo]} (first (sort-by :store.item.photo/index photos))]
    (dom/div
      (->> (css/add-class :content-item)
           (css/add-class :product-item))
      (dom/a
        (->> {:onClick on-click
              :href    goods-href}
             (css/add-class :primary-photo))
        (photo/product-preview product))

      (dom/div
        (->> (css/add-class :header)
            (css/add-class :text))
        (dom/a {:onClick on-click
                :href    goods-href}
               (dom/span nil item-name)))
      (dom/div
        (css/add-class :text)
        (dom/small
          nil
          (dom/span nil "by ")
          (dom/a {:href (routes/store-url store :store)}
                 (dom/span nil (:store.profile/name (:store/profile store))))))

      (dom/div
        (css/add-class :text)
        (dom/strong nil (ui-utils/two-decimal-price price)))
      children)))

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
          on-click #(om/update-state! this assoc :show-item? true)
          #?@(:cljs [open-url? (utils/bp-compare :large breakpoint >)]
              :clj  [open-url? false])]

      (product-element
        {:on-click on-click
         :open-url? open-url?}
        product
        (when show-item?
          (common/modal {:on-close #(om/update-state! this assoc :show-item? false)
                         :size     :large}
                        (product/->Product product)))))))

(def ->ProductItem (om/factory ProductItem))
