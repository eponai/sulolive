(ns eponai.common.ui.store.product-list
  (:require
    [eponai.client.routes :as routes]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.store.common :as store-common]
    #?(:cljs
       [cljsjs.react-grid-layout])
    #?(:cljs
       [goog.crypt.base64 :as crypt])
    #?(:cljs
       [eponai.web.utils :as web-utils])
    [om.next :as om :refer [defui]]
    [om.dom :as om-dom]
    [taoensso.timbre :refer [debug]]
    [eponai.common.format.date :as date]
    [eponai.common.ui.utils :as utils]
    [eponai.common.ui.elements.table :as table]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.photo :as p]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.web.ui.photo :as photo]
    [eponai.common :as c]
    [clojure.string :as string]
    [eponai.client.parser.message :as msg]))

(defn products->grid-layout [component products]
  (let [num-cols 3
        layout-fn (fn [i p]
                    {:x (mod i num-cols)
                     :y (int (/ i num-cols))
                     :w 1
                     :h 1
                     :i (str (:db/id p))})]
    (map-indexed layout-fn products)))

(defn grid-layout->products [component layout]
  (let [{:keys [query/inventory]} (om/props component)
        grouped-by-id (group-by #(str (:db/id %)) inventory)
        _ (debug "Items by id; " grouped-by-id)
        items-by-position (reduce (fn [m grid-item]
                                    (if (map? grid-item)
                                      (let [product (first (get grouped-by-id (:i grid-item)))]
                                        (assoc m (str (:x grid-item) (:y grid-item)) product))
                                      (let [product (first (get grouped-by-id (.-i grid-item)))]
                                        (assoc m (str (.-x grid-item) (.-y grid-item)) product))))
                                  (sorted-map)
                                  layout)]
    (debug "Sorted items: " (sort-by #(string/reverse (key %)) items-by-position))
    (map-indexed
      (fn [i [_ product]]
        (assoc product :store.item/index i))
      (sort-by #(string/reverse (key %)) items-by-position))))

(defn product-element [component p]
  (let [{:query/keys [current-route]} (om/props component)
        {:store.item/keys [price]
         item-name        :store.item/name} p
        {:keys [grid-editable?]} (om/get-state component)]
    (dom/a
      (cond->> (->> (when-not grid-editable?
                      {:href (routes/url :store-dashboard/product (assoc (:route-params current-route) :product-id (:db/id p)))})
                 (css/add-class :content-item)
                    (css/add-class :product-item))
               grid-editable?
               (css/add-class :product-move))
      (dom/div
        (->>
          (css/add-class :primary-photo))
        (photo/product-preview p
                               {}
                               (p/overlay nil)))

      (dom/div
        (->> (css/add-class :header)
             (css/add-class :text))
        (dom/div
          nil
          (dom/span nil item-name)))
      (dom/div
        (css/add-class :text)
        (dom/strong nil (utils/two-decimal-price price))))))

(def row-heights {:xxlarge 350
                  :xlarge  350
                  :large   400
                  :medium  350
                  :small   350
                  :tiny    250})
(defui ProductList

  static om/IQuery
  (query [_]
    [{:query/inventory [:store.item/name
                        :store.item/description
                        {:store.item/photos [{:store.item.photo/photo [:photo/path]}
                                             :store.item.photo/index]}]}
     :query/current-route])

  static store-common/IDashboardNavbarContent
  (render-subnav [_ _]
    (dom/div nil))

  (subnav-title [_ _]
    "Products")

  Object
  (update-layout [this]
    #?(:cljs
       (let [{:keys [query/inventory]} (om/props this)
             WidthProvider (.-WidthProvider (.-ReactGridLayout js/window))
             grid-element (WidthProvider (.-Responsive (.-ReactGridLayout js/window)))
             size js/window.innerWidth
             breakpoint (if (> 460 size) :tiny (web-utils/breakpoint size))

             sorted-products (sort-by :store.item/index inventory)]
         (debug "State update in did mount size: ")
         (om/update-state! this assoc
                           :grid-element grid-element
                           :breakpoint breakpoint
                           :layout (clj->js (products->grid-layout this sorted-products))))))

  (save-product-order [this]
    (let [{:keys [layout]} (om/get-state this)
          {:keys [query/current-route]} (om/props this)
          products (grid-layout->products this layout)]
      (debug "Save products: " (into [] (grid-layout->products this layout)))
      (msg/om-transact! this [(list 'store/update-product-order {:items products
                                                                 :store-id (get-in current-route [:route-params :store-id])})
                              :query/inventory])
      (om/update-state! this assoc :grid-editable? false)))

  (componentDidMount [this]
    (debug "State component did mount")
    (.update-layout this))
  (on-drag-stop [this layout]
    (om/update-state! this assoc :layout (products->grid-layout this (grid-layout->products this layout))))
  (on-breakpoint-change [this bp]
    #?(:cljs
       (let [size js/window.innerWidth
             breakpoint (if (> 460 size) :tiny (web-utils/breakpoint size))]
         (debug "Updating breakpoint: " breakpoint)
         ;(om/update-state! this assoc :breakpoint bp-key)
         (when-not (= breakpoint (:breakpoint (om/get-state this)))
           (.update-layout this))
         )))
  (initLocalState [this]
    {:cols {:xxlarge 3 :xlarge 3 :large 3 :medium 3 :small 2 :tiny 2}})
  (render [this]
    (let [{:keys [query/inventory]} (om/props this)
          {:keys          [search-input cols layout grid-element grid-editable? breakpoint]
           :products/keys [selected-section edit-sections listing-layout]} (om/get-state this)
          {:keys [route-params store]} (om/get-computed this)
          products (sort-by :store.item/index
                            (if (not-empty search-input)
                              (filter #(clojure.string/includes? (.toLowerCase (:store.item/name %))
                                                                 (.toLowerCase search-input)) inventory)
                              inventory))]
      (dom/div
        {:id "sulo-product-list"}
        (dom/h1
          (css/show-for-sr) "Products")
        (dom/div
          (css/add-class :section-title)
          (dom/h2 nil "Products")
          (dom/a (css/button {:href (routes/url :store-dashboard/create-product
                                                {:store-id (:store-id route-params)
                                                 :action   "create"})})
                 "Add product"))
        (callout/callout
          nil
          (grid/row
            (css/add-classes [:expanded :collapse])
            (grid/column
              nil
              (dom/input {:value       (or search-input "")
                          :onChange    #(om/update-state! this assoc :search-input (.. % -target -value))
                          :placeholder "Search Products..."
                          :type        "text"}))
            (grid/column
              (->> (css/add-class :shrink)
                   (css/text-align :right))
              (dom/a
                (->> (css/button-hollow {:onClick #(om/update-state! this assoc :products/listing-layout :products/list)})
                     (css/add-class :secondary))
                (dom/i {:classes ["fa fa-list"]}))
              (dom/a
                (->> (css/button-hollow
                       {:onClick #(om/update-state! this assoc :products/listing-layout :products/grid)})
                     (css/add-class :secondary))
                (dom/i {:classes ["fa fa-th"]})))
            )
          (if (= listing-layout :products/list)
            (table/table
              (css/add-class :hover)
              (dom/div
                (css/add-class :thead)
                (dom/div
                  (css/add-class :tr)
                  (dom/span (css/add-class :th) (dom/span nil ""))
                  (dom/span (css/add-class :th) "Product Name")
                  (dom/span
                    (->> (css/add-class :th)
                         (css/text-align :right)) "Price")
                  (dom/span
                    (->> (css/add-class :th)
                         (css/show-for :medium)) "Last Updated")))
              (dom/div
                (css/add-class :tbody)
                (map (fn [p]
                       (let [product-link (routes/url :store-dashboard/product
                                                      {:store-id   (:store-id route-params)
                                                       :product-id (:db/id p)})]
                         (dom/a
                           (css/add-class :tr {:href product-link})
                           (dom/span (css/add-class :td)
                                     (p/product-preview p {:transformation :transformation/thumbnail-tiny}))
                           (dom/span (css/add-class :td)
                                     (:store.item/name p))
                           (dom/span
                             (->> (css/add-class :td)
                                  (css/text-align :right))
                             (utils/two-decimal-price (:store.item/price p)))
                           (dom/span
                             (->> (css/add-class :td)
                                  (css/show-for :medium))
                             (when (:store.item/updated p)
                               (date/date->string (* 1000 (:store.item/updated p)) "MMM dd yyyy HH:mm"))))))
                     products)))

            #?(:cljs
               (dom/div
                 nil
                 (if grid-editable?
                   (dom/div
                     nil
                     (store-common/save-button {:onClick #(.save-product-order this)})
                     (store-common/cancel-button {:onClick #(do
                                                             (.update-layout this)
                                                             (om/update-state! this assoc :grid-editable? false))}))
                   (store-common/edit-button
                     {:onClick #(om/update-state! this assoc :grid-editable? true)}
                     (dom/span nil "Edit layout")))
                 (grid/row
                   (css/add-class :collapse)
                   (grid/column
                     nil
                     (when (and (some? layout) (some? grid-element) (not-empty products))
                       (do
                         (debug "Creating grid layout with row-hweight: " (get row-heights breakpoint) " breakpoint " breakpoint)
                         (.createElement
                           (.-React js/window)
                           grid-element
                           (clj->js {:className          (if grid-editable? "layout editable animate" "layout"),
                                     :draggableHandle    ".product-move"
                                     :layouts            {:xxlarge layout :xlarge layout :large layout :medium layout :small layout :tiny layout}
                                     :breakpoints        {:xxlarge 1440, :xlarge 1200, :large 1024, :medium 750, :small 460 :tiny 0}
                                     :rowHeight          (get row-heights breakpoint)
                                     :cols               cols
                                     :useCSSTransforms   true
                                     :isDraggable        grid-editable?
                                     :isResizable        false
                                     :verticalCompact    true
                                     :onBreakpointChange #(.on-breakpoint-change this %)
                                     ;:onResizeStart    #(.edit-start this)
                                     ;:onResizeStop     #(.edit-stop this nil %)
                                     ;:onDragStart        #(.edit-start this)
                                     ;:onDrag           #(.on-item-drag this %)
                                     :onDragStop         #(.on-drag-stop this %)
                                     })
                           (into-array
                             (map (fn [p]
                                    (dom/div
                                      {:key (str (:db/id p))}
                                      (product-element this p)))
                                  products))))))))
               :clj

               (grid/products
                 products
                 (fn [p]
                   (product-element this p))))))))))

(def ->ProductList (om/factory ProductList))
