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
    [taoensso.timbre :refer [debug]]
    [eponai.common.format.date :as date]
    [eponai.common.ui.utils :as utils]
    [eponai.common.ui.elements.table :as table]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.photo :as photo]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.web.ui.photo :as photo]
    [clojure.string :as string]
    [eponai.client.parser.message :as msg]
    [eponai.web.ui.button :as button]
    [eponai.common.mixpanel :as mixpanel]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.common :as common]))

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
                               (photo/overlay nil)))

      (dom/div
        (->> (css/add-class :header)
             (css/add-class :text))
        (dom/div
          nil
          (dom/span nil item-name)))
      (dom/div
        (css/add-class :text)
        (dom/strong nil (utils/two-decimal-price price))))))

(defn edit-sections-modal [component]
  (let [{:products/keys [edit-sections]} (om/get-state component)
        {:query/keys [store]} (om/props component)]
    (when (some? edit-sections)
      (common/modal
        {:on-close #(om/update-state! component dissoc :products/edit-sections)}
        (let [items-by-section (group-by #(get-in % [:store.item/section :db/id]) (:store/items store))]
          (dom/div
            nil
            (dom/p (css/add-class :header) "Edit sections")
            (menu/vertical
              (css/add-class :edit-sections-menu)
              (map-indexed
                (fn [i s]
                  (let [no-items (count (get items-by-section (:db/id s)))]
                    (menu/item (css/add-class :edit-sections-item)
                               ;(dom/a nil (dom/i {:classes ["fa "]}))
                               (dom/input
                                 {:type        "text"
                                  :id          (str "input.section-" i)
                                  :placeholder "New section"
                                  :value       (:store.section/label s "")
                                  :onChange    #(om/update-state! component update :products/edit-sections
                                                                  (fn [sections]
                                                                    (let [old (get sections i)
                                                                          new (assoc old :store.section/label (.-value (.-target %)))]
                                                                      (assoc sections i new))))})
                               (if (= 1 no-items)
                                 (dom/small nil (str no-items " item"))
                                 (dom/small nil (str no-items " items")))
                               (button/user-setting-default
                                 {:onClick #(om/update-state! component update :products/edit-sections
                                                              (fn [sections]
                                                                (into [] (remove nil? (assoc sections i nil)))))}
                                 (dom/span nil "Remove")))))
                edit-sections)
              (menu/item
                (css/add-class :edit-sections-item)
                (button/user-setting-default
                  {:onClick #(om/update-state! component update :products/edit-sections conj {})}
                  (dom/span nil "Add section..."))))

            (dom/div
              (->> (css/text-align :right)
                   (css/add-class :action-buttons))
              (button/cancel {:onClick #(om/update-state! component dissoc :products/edit-sections)})
              (button/save {:onClick #(.save-sections component)}))))))))

(def row-heights {:xxlarge 370
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
                        :store.item/index
                        {:store.item/section [:db/id]}
                        {:store.item/photos [{:store.item.photo/photo [:photo/path]}
                                             :store.item.photo/index]}]}
     {:query/store [{:store/sections [:db/id
                                      :store.section/label]}]}
     {:query/ui-state [:ui.singleton.state/product-view]}
     :query/messages
     :query/current-route])

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
      (msg/om-transact! this [(list 'store/update-product-order {:items    products
                                                                 :store-id (get-in current-route [:route-params :store-id])})
                              :query/inventory])
      (om/update-state! this assoc :grid-editable? false)))
  (save-sections [this]
    (let [{:products/keys [edit-sections]} (om/get-state this)
          {:query/keys [current-route]} (om/props this)
          new-sections (filter #(not-empty (string/trim (:store.section/label % ""))) edit-sections)]
      (msg/om-transact! this [(list 'store/update-sections {:sections new-sections
                                                            :store-id (get-in current-route [:route-params :store-id])})
                              :query/store])
      (om/update-state! this dissoc :products/edit-sections)))

  (change-product-view [this view]
    (debug "Transacting new product view: " view)
    (om/transact! this [(list 'dashboard/change-product-view {:product-view view})
                        :query/ui-state])
    (om/update-state! this assoc :grid-editable? false))

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
    {:cols                      {:xxlarge 3 :xlarge 3 :large 3 :medium 3 :small 2 :tiny 2}
     :products/listing-layout   :products/list
     :products/selected-section :all})
  (render [this]
    (let [{:query/keys [store inventory ui-state]} (om/props this)
          {:keys          [search-input cols layout grid-element grid-editable? breakpoint]
           :products/keys [selected-section edit-sections]} (om/get-state this)
          product-view (:ui.singleton.state/product-view ui-state)
          {:keys [route-params]} (om/get-computed this)
          products (if grid-editable?
                     (sort-by :store.item/index inventory)
                     (cond->> (sort-by :store.item/index inventory)
                              (not= selected-section :all)
                              (filter #(= selected-section (get-in % [:store.item/section :db/id])))
                              (not-empty search-input)
                              (filter #(clojure.string/includes? (.toLowerCase (:store.item/name %))
                                                                 (.toLowerCase search-input)))))
          ;(sort-by :store.item/index
          ;                  (if (not-empty search-input)
          ;                    (filter #(clojure.string/includes? (.toLowerCase (:store.item/name %))
          ;                                                       (.toLowerCase search-input)) inventory)
          ;                    inventory)
          ;                                                                                            )
          ]
      ;(cond->> (sort-by :store.item/index items)
      ;         (not= selected-section :all)
      ;         (filter #(= selected-section (get-in % [:store.item/section :db/id])))
      ;         (not-empty search-input)
      ;         (filter #(clojure.string/includes? (.toLowerCase (:store.item/name %))
      ;                                            (.toLowerCase search-input))))
      (dom/div
        {:id "sulo-product-list"}

        (edit-sections-modal this)
        (dom/div
          (css/add-class :section-title)
          (dom/h1 nil "Products"))

        (callout/callout
          (css/add-class :edit-sections-callout)
          (grid/row
            (css/add-classes [:collapse :expanded])
            (grid/column
              nil
              (menu/horizontal
                (css/add-class :product-section-menu)
                (menu/item
                  (when (= selected-section :all)
                    (css/add-class :is-active))
                  (dom/a
                    {:onClick #(om/update-state! this assoc :products/selected-section :all)}
                    (dom/span nil "All items")))
                (map (fn [s]
                       (menu/item
                         (when (= selected-section (:db/id s))
                           (css/add-class :is-active))
                         (dom/a {:onClick #(om/update-state! this assoc :products/selected-section (:db/id s))}
                                (dom/span nil (string/capitalize (:store.section/label s))))))
                     (:store/sections store))))
            (grid/column
              (->> (css/text-align :right)
                   (grid/column-size {:small 12 :medium 3 :large 2}))
              (button/user-setting-default
                {:onClick #(do
                            (mixpanel/track "Store: Edit product sections")
                            (om/update-state! this assoc :products/edit-sections (into [] (:store/sections store))))}
                (dom/span nil "Edit sections")))))

        (callout/callout
          (css/add-class :submenu)

          (dom/div
            (css/hide-for :medium)
            (menu/horizontal
              nil
              (menu/item
                (when (= product-view :products/list)
                  (css/add-class :is-active))
                (dom/a
                  {:onClick #(.change-product-view this :products/list)}
                  (dom/i {:classes ["fa fa-list"]})
                  (dom/span (css/show-for-sr) "List")))
              (menu/item
                (when (= product-view :products/grid)
                  (css/add-class :is-active))
                (dom/a
                  {:onClick #(.change-product-view this :products/grid)}
                  (dom/i {:classes ["fa fa-th"]})
                  (dom/span (css/show-for-sr) "Grid")))
              (menu/item
                (css/add-class :button-item)
                (dom/div
                  (css/text-align :right)
                  (button/button-small
                    {:href    (routes/url :store-dashboard/create-product
                                          {:store-id (:store-id route-params)
                                           :action   "create"})
                     :onClick #(mixpanel/track "Store: Add product")}
                    "Add product"))))
            (dom/input {:value       (or search-input "")
                        :onChange    #(om/update-state! this assoc :search-input (.. % -target -value))
                        :placeholder "Search Products..."
                        :type        "text"}))

          (menu/horizontal
            (css/show-for :medium)
            (menu/item
              (when (= product-view :products/list)
                (css/add-class :is-active))
              (dom/a
                {:onClick #(.change-product-view this :products/list)}
                (dom/i {:classes ["fa fa-list"]})))
            (menu/item
              (when (= product-view :products/grid)
                (css/add-class :is-active))
              (dom/a
                {:onClick #(.change-product-view this :products/grid)}
                (dom/i {:classes ["fa fa-th"]})))
            (menu/item
              (css/add-class :search-input)
              (dom/input {:value       (or search-input "")
                          :onChange    #(om/update-state! this assoc :search-input (.. % -target -value))
                          :placeholder "Search Products..."
                          :type        "text"}))
            (menu/item
              nil
              (button/button-small
                {:href    (routes/url :store-dashboard/create-product
                                      {:store-id (:store-id route-params)
                                       :action   "create"})
                 :onClick #(mixpanel/track "Store: Add product")}
                "Add product"))))




        (if (= product-view :products/list)
          (callout/callout
            nil
            (table/table
              (css/add-class :hover)
              (dom/div
                (css/add-class :thead)
                (dom/div
                  (css/add-class :tr)
                  (dom/span (css/add-class :th) (dom/span nil ""))
                  (dom/span (css/add-class :th) "Product name")
                  (dom/span
                    (->> (css/add-class :th)
                         (css/text-align :right)) "Price")))
              (dom/div
                (css/add-class :tbody)
                (map (fn [p]
                       (let [product-link (routes/url :store-dashboard/product
                                                      {:store-id   (:store-id route-params)
                                                       :product-id (:db/id p)})]
                         (dom/a
                           (css/add-class :tr {:href product-link})
                           (dom/span (css/add-class :td)
                                     (photo/product-preview p {:transformation :transformation/thumbnail-tiny}))
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
                     products))))

          (callout/callout
            nil
            (grid/row
              (css/add-class :expanded)
              (grid/column
                (css/add-class :shrink)
                (dom/div
                  nil
                  (if grid-editable?
                    (dom/div
                      (css/add-class :action-buttons)
                      (button/save
                        (css/add-class :small {:onClick #(.save-product-order this)}))
                      (button/cancel
                        (css/add-class :small {:onClick #(do
                                                          (.update-layout this)
                                                          (om/update-state! this assoc :grid-editable? false))})))

                    (button/edit
                      (css/add-class :small {:onClick #(om/update-state! this assoc :grid-editable? true :products/selected-section :all)})
                      (dom/span nil "Edit layout")))))
              (grid/column
                nil
                (when grid-editable?
                  (callout/callout-small
                    (cond->> (css/add-class :warning)
                             (not grid-editable?)
                             (css/add-class :sl-invisible))
                    (dom/small nil "This is how your products will appear in your store. Try moving them around and find a layout you like and save when you're done.")))))
            (dom/div
              nil

              #?(:cljs
                 (when (and grid-editable? (some? layout) (some? grid-element) (not-empty products))
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
                              products)))))))
            (when-not grid-editable?
              (grid/products
                products
                (fn [p]
                  (product-element this p))))))))))

(def ->ProductList (om/factory ProductList))
