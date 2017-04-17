(ns eponai.common.ui.store.product-list
  (:require
    [eponai.client.routes :as routes]
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    [om.dom :as dom]
    #?(:cljs
       [goog.crypt.base64 :as crypt])
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.format.date :as date]
    [eponai.common.ui.utils :as utils]
    [eponai.common.ui.elements.photo :as photo]))

(defn primary-photo [product]
  (let [item-photo (first (sort-by :store.item.photo/index (:store.item/photos product)))]
    (get item-photo :store.item.photo/photo)))

(defui ProductList
  static om/IQuery
  (query [_]
    [{:query/inventory [:store.item/name
                        :store.item/description
                        :store.item/uuid
                        {:store.item/photos [{:store.item.photo/photo [:photo/path]}
                                             :store.item.photo/index]}]}])
  Object
  (render [this]
    (let [{:keys [query/inventory]} (om/props this)
          {:keys [route-params]} (om/get-computed this)]
      ;(debug "Render product list: " inventory)
      ;#?(:cljs
      ;   (do
      ;     (debug "Convert id: " (:store.item/uuid (first inventory)))
      ;     (let [b (crypt/encodeString (:store.item/uuid (first inventory)) true)]
      ;       (debug "Converted uuid: " b))))
      (dom/div nil
        (my-dom/div
          (->> (css/grid-row))
          (my-dom/div
            (css/grid-column)
            (dom/h3 nil "Products"))
          (my-dom/div
            (->> (css/grid-column)
                 (css/text-align :right))
            (dom/a #js {:className "button"
                        :href      (routes/url :store-dashboard/create-product
                                               {:store-id (:store-id route-params)
                                                :action   "create"})}
                   "Add product")))
        (my-dom/div
          (->> (css/grid-row)
               (css/grid-column))
          ;(my-dom/div
          ;  {:className "callout transparent"})
          (my-dom/input {:value       ""
                         :placeholder "Search Products..."
                         :type        "text"}))

        (my-dom/div
          (->> (css/grid-row))
          (my-dom/div
            (->> (css/grid-column))
            (dom/table
              #js {:className "hover"}
              (dom/thead
                nil
                (dom/tr nil
                        (dom/th nil (dom/span nil ""))
                        (dom/th nil "Product Name")
                        (dom/th nil "Price")
                        (dom/th nil "Last Updated")))
              (dom/tbody
                nil
                (map-indexed (fn [i p]
                       (debug "PRODUCT: " p)
                       (let [product-link (routes/url :store-dashboard/product
                                                      {:store-id   (:store-id route-params)
                                                       :product-id (:db/id p)})]
                         (dom/tr #js {:key (str i)}
                                 (dom/td nil
                                         (photo/product-photo (primary-photo p)))
                                 (dom/td nil
                                         (dom/a #js {:href product-link}
                                                (dom/span nil (:store.item/name p))))
                                 (dom/td nil
                                         (dom/a #js {:href product-link}
                                                (dom/span nil (utils/two-decimal-price (:store.item/price p)))))
                                 (dom/td nil
                                         (when (:store.item/updated p)
                                           (dom/a #js {:href product-link}
                                                  (dom/span nil (date/date->string (* 1000 (:store.item/updated p)) "MMM dd yyyy HH:mm"))))))))
                     inventory)))))))))

(def ->ProductList (om/factory ProductList))
