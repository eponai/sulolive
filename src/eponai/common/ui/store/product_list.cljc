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
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.table :as table]))

(defn primary-photo [product]
  (let [item-photo (first (sort-by :store.item.photo/index (:store.item/photos product)))]
    (get item-photo :store.item.photo/photo)))

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
          (my-dom/input {:value       (or search-input "")
                         :onChange    #(om/update-state! this assoc :search-input (.. % -target -value))
                         :placeholder "Search Products..."
                         :type        "text"}))

        (my-dom/div
          (->> (css/grid-row))
          (my-dom/div
            (->> (css/grid-column))
            (table/table
              (css/add-class :hover)
              (my-dom/div
                (css/add-class :thead)
                (my-dom/div
                  (css/add-class :tr)
                        (my-dom/span (css/add-class :th) (dom/span nil ""))
                        (my-dom/span (css/add-class :th) "Product Name")
                        (my-dom/span (css/add-class :th) "Price")
                        (my-dom/span (css/add-class :th) "Last Updated")))
              (my-dom/div
                (css/add-class :tbody)
                (map (fn [p]
                       (let [product-link (routes/url :store-dashboard/product
                                                      {:store-id   (:store-id route-params)
                                                       :product-id (:db/id p)})]
                         (my-dom/a
                           (css/add-class :tr {:href product-link})
                           (my-dom/span (css/add-class :td)
                                        (photo/product-photo (primary-photo p)))
                           (my-dom/span (css/add-class :td)
                                        (:store.item/name p))
                           (my-dom/span (css/add-class :td)
                                        (utils/two-decimal-price (:store.item/price p)))
                           (my-dom/span (css/add-class :td)
                                        (when (:store.item/updated p)
                                          (date/date->string (* 1000 (:store.item/updated p)) "MMM dd yyyy HH:mm"))))))
                             products)))))))))

(def ->ProductList (om/factory ProductList))
