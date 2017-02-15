(ns eponai.common.ui.store.product-list
  (:require
    [eponai.client.routes :as routes]
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]))

(defui ProductList
  Object
  (render [this]
    (let [store (om/props this)]
      (debug "Render product list")
      (dom/div nil
        (my-dom/div
          (->> (css/grid-row))
          (my-dom/div
            (->> (css/grid-column)
                 (css/text-align :right))
            (dom/a #js {:className "button"
                        :href      (routes/url :store-dashboard/create-product
                                               {:store-id (:db/id store)
                                                :action   "create"})}
                   "Add product")))

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
                                                      {:store-id   (:db/id store)
                                                       :product-id (:db/id p)})]
                         (dom/tr nil
                                 (dom/td nil
                                         (dom/input #js {:type "checkbox"}))
                                 (dom/td nil
                                         (dom/a #js {:href product-link}
                                                (dom/span nil (:store.item/name p))))
                                 (dom/td nil
                                         (dom/a #js {:href product-link}
                                                (dom/span nil (:store.item/price p))))
                                 (dom/td nil
                                         (dom/a #js {:href product-link}
                                                (dom/span nil (:store.item/price p)))))))
                     (:store/items store))))))))))

(def ->ProductList (om/factory ProductList))
