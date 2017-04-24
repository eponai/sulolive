(ns eponai.common.ui.checkout.review
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.utils :as utils]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.css :as css]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]))

(defn compute-item-price [items]
  (reduce + (map #(get-in % [:store.item/_skus :store.item/price]) items)))

(defn store-element [s]
  (debug "Store element: " s)
  (let [{:store/keys [photo] store-name :store/name} s]
    (my-dom/div
      (->> (css/grid-row)
           (css/add-class :expanded)
           (css/add-class :store-container)
           (css/align :center)
           )
      (my-dom/div
        (->> (css/grid-column)
             (css/grid-column-size {:small 3 :medium 2}))
        (photo/circle {:src (:photo/path photo)}))
      (my-dom/div
        (->> (css/grid-column)
             (css/grid-column-size {:small 12})
             (css/text-align :center))
        (dom/div nil (dom/p nil (dom/strong #js {:className "store-name"} store-name)))))))

(defui CheckoutReview
  Object
  (render [this]
    (let [items (om/props this)
          item-count (count items)]
      (debug "CheckoutReview " (om/props this))
      (dom/div nil
        ;(my-dom/div
        ;  (css/grid-row)
        ;  (my-dom/div
        ;    (css/grid-column)
        ;    (dom/h3 nil "Review & Confirm")))
        (dom/div #js {:className "callout"}
          (let [store (:store/_items (:store.item/_skus (first items)))]
            (debug "store: " store)
            (store-element store))
          (dom/div #js {:className "items"}
            (map-indexed
              (fn [i sku]
                   (debug "SKU: " sku)
                   (let [{:store.item/keys [price photos]
                          product-id       :db/id
                          item-name        :store.item/name} (get sku :store.item/_skus)
                         sorted-photos (sort-by :store.item.photo/index photos)]
                     (my-dom/div
                       (->> (css/grid-row {:key i})
                            ;(css/add-class :collapse)
                            (css/align :middle)
                            ;(css/add-class :callout)
                            (css/add-class :transparent)
                            (css/add-class :item))

                       (my-dom/div
                         (->> (css/grid-column)
                              (css/grid-column-size {:small 4 :medium 2}))
                         (photo/square
                           {:src (get-in (first sorted-photos) [:store.item.photo/photo :photo/path])}))

                       ;(my-dom/div
                       ;  (->> (css/grid-column))
                       ;  (dom/a #js {:className "close-button"} (dom/small nil "x")))
                       (my-dom/div
                         (->> (css/grid-column))

                         (dom/div #js {:className ""}
                           (dom/a #js {
                                       ;:href      (routes/url :product {:product-id product-id})
                                       :className "name"}
                                  (dom/span nil item-name)))
                         (dom/div #js {:className ""}
                           (dom/span nil (:store.item.sku/variation sku))))

                       (my-dom/div
                         (->> (css/grid-column)
                              (css/align :right)
                              ;(css/add-class :shrink)
                              (css/grid-column-size {:small 3 :medium 2})
                              ;(css/grid-column-offset {:small 3 :large 0})
                              )
                         (dom/input #js {:type         "number"
                                         :defaultValue "1"})
                         )
                       (my-dom/div
                         (->> (css/grid-column)
                              (css/text-align :right)
                              (css/add-class :shrink)
                              )
                         (dom/div #js {:className ""}
                           (dom/span #js {:className "price"}
                                     (utils/two-decimal-price price)))))))
                 items))
          (dom/div #js {:className "receipt"}
            (my-dom/div
              (css/grid-row)
              (my-dom/div
                (->> (css/grid-column)
                     (css/text-align :right))
                (dom/span nil "Subtotal"))
              (my-dom/div
                (->> (css/grid-column)
                     (css/text-align :right))
                (dom/span nil (utils/two-decimal-price (compute-item-price items)))))
            (my-dom/div
              (css/grid-row)
              (my-dom/div
                (->> (css/grid-column)
                     (css/text-align :right))
                (dom/span nil "Shipping"))
              (my-dom/div
                (->> (css/grid-column)
                     (css/text-align :right))
                (dom/span nil (utils/two-decimal-price 5))))
            (my-dom/div
              (css/grid-row)
              (my-dom/div
                (->> (css/grid-column)
                     (css/text-align :right))
                (dom/span nil "Discount"))
              ;(my-dom/div
              ;  (->> (css/grid-column)
              ;       (css/text-align :right))
              ;  (dom/a nil "Add code"))
              (my-dom/div
                (->> (css/grid-column)
                     (css/text-align :right))
                (dom/span nil (utils/two-decimal-price 0))
                (dom/br nil)
                (dom/a nil "Add code")))
            (my-dom/div
              (css/add-class :total)
              (my-dom/div
                (css/grid-row)
                (my-dom/div
                  (->> (css/grid-column)
                       (css/text-align :right))
                  (dom/strong nil "Total"))
                (my-dom/div
                  (->> (css/grid-column)
                       (css/text-align :right))
                  (dom/strong nil (utils/two-decimal-price (+ 5 (compute-item-price items)))))))))
        ))))

(def ->CheckoutReview (om/factory CheckoutReview))