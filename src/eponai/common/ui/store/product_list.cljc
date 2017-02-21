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
    [eponai.common.format.date :as date]))

(defui ProductList
  static om/IQuery
  (query [_]
    [:query/inventory])
  Object
  (render [this]
    (let [{:keys [query/inventory]} (om/props this)
          {:keys [route-params]} (om/get-computed this)]
      (debug "Render product list")
      #?(:cljs
         (do
           (debug "Convert id: " (:product/id (first inventory)))
           (let [b (crypt/encodeString (:product/id (first inventory)) true)]
             (debug "Converted uuid: " b))))
      (dom/div nil
        (my-dom/div
          (->> (css/grid-row))
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
                        (dom/th nil (dom/span nil "Delete"))
                        (dom/th nil "Product Name")
                        (dom/th nil "Price")
                        (dom/th nil "Last Updated")))
              (dom/tbody
                nil
                (map (fn [p]
                       (let [product-link (routes/url :store-dashboard/product
                                                      {:store-id   (:store-id route-params)
                                                       :product-id (:product/id p)})]
                         (dom/tr nil
                                 (dom/td nil
                                         (dom/input #js {:type "checkbox"}))
                                 (dom/td nil
                                         (dom/a #js {:href product-link}
                                                (dom/span nil (:product/name p))))
                                 (dom/td nil
                                         (dom/a #js {:href product-link}
                                                (dom/span nil (:product/price p))))
                                 (dom/td nil
                                         (dom/a #js {:href product-link}
                                                (dom/span nil (date/date->string (* 1000 (:product/updated p)))))))))
                     inventory)))))))))

(def ->ProductList (om/factory ProductList))
