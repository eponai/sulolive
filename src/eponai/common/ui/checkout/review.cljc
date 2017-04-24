(ns eponai.common.ui.checkout.review
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.utils :as utils]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.css :as css]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.callout :as callout]))

(defn compute-item-price [items]
  (reduce + (map #(get-in % [:store.item/_skus :store.item/price]) items)))

(defn store-element [s]
  (debug "Store element: " s)
  (let [{:store/keys [photo] store-name :store/name} s]
    (grid/row
      (->> (css/add-class :expanded)
           (css/add-class :store-container)
           (css/align :center))
      (grid/column
        (grid/column-size {:small 3 :medium 2})
        (photo/circle {:src (:photo/path photo)}))
      (grid/column
        (->> (grid/column-size {:small 12})
             (css/text-align :center))
        (dom/p nil (dom/strong
                     (css/add-class :store-name) store-name))))))

(defui CheckoutReview
  Object
  (render [this]
    (let [{:keys [items subtotal shipping]} (om/props this)
          item-count (count items)]
      (debug "CheckoutReview " (om/props this))
      (callout/callout
        nil
        (let [store (:store/_items (:store.item/_skus (first items)))]
          (debug "store: " store)
          (store-element store))
        (dom/div
          (css/add-class :sl-CheckoutItemlist)
          (map
            (fn [sku]
              (debug "SKU: " sku)
              (let [{:store.item/keys [price photos]
                     product-id       :db/id
                     item-name        :store.item/name} (get sku :store.item/_skus)
                    sorted-photos (sort-by :store.item.photo/index photos)]
                (dom/div
                  (css/add-class :sl-CheckoutItemlist-row)

                  (dom/div
                    (->> (css/add-class :sl-CheckoutItemlist-cell)
                         (css/add-class :sl-CheckoutItemlist-cell--photo))
                    (photo/square
                      {:src (get-in (first sorted-photos) [:store.item.photo/photo :photo/path])}))
                  (dom/div
                    (css/add-class :sl-CheckoutItemlist-cell)
                    (grid/row
                      nil
                      (grid/column
                        (grid/column-size {:small 12 :medium 8})
                        (dom/p
                          nil
                          (dom/a (css/add-class :name) (dom/span nil item-name)))
                        (dom/p
                          nil
                          (dom/span nil (:store.item.sku/variation sku))))

                      (grid/column
                        (css/clearfix)
                        (dom/input {:type         "number"
                                    :defaultValue "1"}))))

                  (dom/div
                    (->> (css/add-class :sl-CheckoutItemlist-cell)
                         (css/text-align :right))
                    (dom/span
                      (css/add-class :price)
                      (utils/two-decimal-price price))))))
            items))
        (dom/div
          (css/add-class :receipt)
          (grid/row
            nil
            (grid/column
              nil
              (dom/span nil "Subtotal"))
            (grid/column
              (css/text-align :right)
              (dom/span nil (utils/two-decimal-price subtotal))))
          (grid/row
            nil
            (grid/column
              nil
              (dom/span nil "Shipping"))
            (grid/column
              (css/text-align :right)
              (dom/span nil (utils/two-decimal-price shipping))))

          (dom/div
            (css/add-class :total)
            (grid/row
              nil
              (grid/column
                nil
                (dom/strong nil "Total"))
              (grid/column
                (css/text-align :right)
                (dom/strong nil (utils/two-decimal-price (+ shipping subtotal)))))))))))

(def ->CheckoutReview (om/factory CheckoutReview))