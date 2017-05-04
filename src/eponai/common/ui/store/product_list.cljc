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
    [eponai.common.ui.elements.table :as table]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.photo :as p]))

(defui ProductList
  static om/IQuery
  (query [_]
    [{:query/inventory [:store.item/name
                        :store.item/description
                        {:store.item/photos [{:store.item.photo/photo [:photo/path]}
                                             :store.item.photo/index]}]}])
  Object
  (render [this]
    (let [{:keys [query/inventory]} (om/props this)
          {:keys [search-input]} (om/get-state this)
          {:keys [route-params]} (om/get-computed this)
          products (if (not-empty search-input)
                     (filter #(clojure.string/includes? (.toLowerCase (:store.item/name %))
                                                        (.toLowerCase search-input)) inventory)
                     inventory)]
      (dom/div nil
        (grid/row-column
          nil
          (dom/h3 nil "Products"))
        (grid/row
          nil
          (grid/column
            nil
            (my-dom/input {:value       (or search-input "")
                           :onChange    #(om/update-state! this assoc :search-input (.. % -target -value))
                           :placeholder "Search Products..."
                           :type        "text"}))
          (grid/column
            (->> (css/add-class :shrink)
                 (css/text-align :right))
            (dom/a #js {:className "button"
                        :href      (routes/url :store-dashboard/create-product
                                               {:store-id (:store-id route-params)
                                                :action   "create"})}
                   "Add product")))

        (grid/row
          (css/add-class :collapse)
          (grid/column
            nil
            (table/table
              (css/add-class :hover)
              (my-dom/div
                (css/add-class :thead)
                (my-dom/div
                  (css/add-class :tr)
                        (my-dom/span (css/add-class :th) (dom/span nil ""))
                        (my-dom/span (css/add-class :th) "Product Name")
                        (my-dom/span
                          (->> (css/add-class :th)
                               (css/text-align :right)) "Price")
                        (my-dom/span
                          (->> (css/add-class :th)
                               (css/show-for :medium)) "Last Updated")))
              (my-dom/div
                (css/add-class :tbody)
                (map (fn [p]
                       (let [product-link (routes/url :store-dashboard/product
                                                      {:store-id   (:store-id route-params)
                                                       :product-id (:db/id p)})]
                         (my-dom/a
                           (css/add-class :tr {:href product-link})
                           (my-dom/span (css/add-class :td)
                                        (p/product-preview p {:transformation :transformation/thumbnail-tiny}))
                           (my-dom/span (css/add-class :td)
                                        (:store.item/name p))
                           (my-dom/span
                             (->> (css/add-class :td)
                                  (css/text-align :right))
                             (utils/two-decimal-price (:store.item/price p)))
                           (my-dom/span
                             (->> (css/add-class :td)
                                  (css/show-for :medium))
                             (when (:store.item/updated p)
                                          (date/date->string (* 1000 (:store.item/updated p)) "MMM dd yyyy HH:mm"))))))
                             products)))))))))

(def ->ProductList (om/factory ProductList))
