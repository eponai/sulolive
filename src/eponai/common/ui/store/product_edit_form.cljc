(ns eponai.common.ui.store.product-edit-form
  (:require
    [clojure.spec.alpha :as s]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.om-quill :as quill]
    [eponai.common.format :as f]
    [eponai.common.ui.common :as common]
    [eponai.client.parser.message :as msg]
    [eponai.common.ui.elements.css :as css]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug warn]]
    #?(:cljs
       [eponai.client.ui.photo-uploader :as pu])
    #?(:cljs
       [eponai.web.utils :as utils])
    [eponai.client.routes :as routes]
    [eponai.common :as c]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.web.ui.photo :as photo]
    [eponai.common.ui.components.select :as sel]
    [eponai.common.mixpanel :as mixpanel]
    [eponai.web.ui.button :as button]
    [eponai.common.ui.elements.input-validate :as v]
    [eponai.common.database :as db]))

(def form-elements
  {:input-price            "input-price"
   :input-on-sale?         "input-on-sale?"
   :input-sale-price       "input-sale-price"
   :input-name             "input-name"
   :input-desc             "input-desc"
   :input-main-inventory   "input-main-inventory"

   :input-sku-value        "input-sku-value-"
   :input-sku-inventory    "input-sku-inventory-"

   :input-sku-group        "input-sku-group"

   ::select-top-category    "select.top-category"
   ::select-sub-category    "select.sub-category"
   ::select-subsub-category "select.subsub-category"})


(s/def ::select-top-category (s/keys :req [:db/id]))
(s/def ::select-sub-category (s/keys :req [:db/id]))
(s/def ::category-seq (s/keys :req [::select-top-category ::select-sub-category]))
(s/def ::category-single (s/keys :req [::select-top-category]))

(defn get-route-params [component]
  (get-in (om/props component) [:query/current-route :route-params]))


(defn get-element-by-id [id]
  #?(:cljs (.getElementById js/document id)))

(defn get-input-value [id]
  #?(:cljs (when-let [el (get-element-by-id id)]
             (.-value el))))

(defn is-new-product? [component]
  (let [{:keys [product]} (om/get-computed component)]
    (nil? product)))

(defn input-product [component]
  #?(:cljs
     (let [{:keys [uploaded-photos quill-editor selected-category-seq]} (om/get-state component)
           {:keys [input-price input-name input-sku-price input-sku-value input-sku-quantity]} form-elements
           selected-category (last selected-category-seq)]
       {:store.item/name        (utils/input-value-or-nil-by-id input-name)
        :store.item/price       (utils/input-value-or-nil-by-id input-price)
        :store.item/currency    "CAD"
        :store.item/photos      uploaded-photos
        :store.item/description (quill/get-HTML quill-editor)
        :store.item/category    (:db/id selected-category)})))

(defn input-skus [component product]
  #?(:cljs
     (let [{:keys [sku-count]} (om/get-state component)
           {:keys [input-sku-inventory input-sku-value input-main-inventory]} form-elements
           product-skus (:store.item/skus product)]
       (if (< 1 sku-count)
         (remove
           #(nil? (:store.item.sku/variation %))
           (map (fn [index]
                  (let [old-sku (get product-skus index)
                        variation (utils/input-value-or-nil-by-id (str input-sku-value index))
                        inventory (utils/selected-value-by-id (str input-sku-inventory index))
                        sku {:store.item.sku/variation variation
                             :store.item.sku/inventory {:store.item.sku.inventory/value (keyword inventory)}}]
                    (if (some? old-sku)
                      (assoc sku :db/id (:db/id old-sku))
                      sku)))
                (range sku-count)))
         (let [inventory (utils/selected-value-by-id input-main-inventory)]
           [{:store.item.sku/inventory {:store.item.sku.inventory/value (keyword inventory)}}])))))

(defn label-column [opts & content]
  (grid/column
    (grid/column-size {:small 12 :large 2} opts)
    content))

(defn photo-uploader [component index]
  (let [{:keys [did-mount?]} (om/get-state component)]

    (when did-mount?
      #?(:cljs
         (pu/->PhotoUploader (om/computed
                               {:id (str "product-photo-" index)}
                               {:on-photo-queue  (fn [img-result]
                                                   (debug "Got photo: " img-result)
                                                   (om/update-state! component assoc :queue-photo {:src img-result}))
                                :on-photo-upload (fn [photo]
                                                   (mixpanel/track "Store: Upload product photo" photo)
                                                   (om/update-state! component (fn [st]
                                                                                 (-> st
                                                                                     (dissoc :queue-photo)
                                                                                     (update :uploaded-photos conj photo)))))}))))))

(defn empty-photo-button []
  (dom/div
    (css/add-class :empty-photo)
    (dom/div
      nil
      (dom/i {:classes ["fa fa-plus fa-2x"]}))))

(defn selected-section-entity [component]
  (let [{:keys [selected-section]} (om/get-state component)]
    (when (:label selected-section)
      (cond-> {:store.section/label (:label selected-section)}
              (number? (:value selected-section))
              (assoc :db/id (:value selected-section))))))

(defn category-seq [component c]
  (let [{:query/keys [categories]} (om/props component)
        product-category (loop [cur c
                                l []]
                           (if (not-empty (:category/_children cur))
                             (recur (:category/_children cur) (cons cur l))
                             (cons cur l)))]
    (loop [cs categories
           cat-seq product-category
           l []]
      (if-let [c (first cat-seq)]
        (let [new-c (some #(when (= (:db/id %) (:db/id c)) %) cs)]
          (recur (:category/children new-c) (rest cat-seq) (conj l new-c)))
        l))))

(defui ProductEditForm
  static om/IQuery
  (query [_]
    [:query/current-route
     :query/messages
     {:query/navigation [:category/name :category/label :category/path :category/route-map]}
     {:query/categories [:db/id :category/name :category/label
                         :category/_children
                         {:category/children [:db/id :category/name :category/label
                                              :category/_children
                                              {:category/children [:db/id :category/name :category/label
                                                                   :category/_children]}]}]}
     {:query/item [:store.item/name
                   :store.item/description
                   :store.item/price
                   {:store.item/section [:db/id :store.section/label]}
                   {:store.item/skus [:db/id
                                      :store.item.sku/inventory
                                      :store.item.sku/variation]}
                   {:store.item/photos [{:store.item.photo/photo [:photo/id]}
                                        :store.item.photo/index]}
                   {:store.item/category [:category/label
                                          :category/path
                                          :db/id
                                          {:category/_children [:db/id :category/label :category/path
                                                                {:category/_children [:db/id :category/label :category/path]}]}]}]}])

  Object
  (componentDidUpdate [this _ _]
    (when-let [action-finished (some #(when (msg/final? (msg/last-message this %)) %)
                                     ['store/update-product
                                      'store/create-product
                                      'store/delete-product])]
      (msg/clear-one-message! this action-finished)
      (routes/set-url! this :store-dashboard/product-list {:store-id (:store-id (get-route-params this))})))
  (delete-product [this]
    (let [{:keys [product-id store-id]} (get-route-params this)]
      (mixpanel/track "Store: Delete product" {:product-id product-id})
      (msg/om-transact! this `[(store/delete-product ~{:store-id (db/store-id->dbid this store-id)
                                                       :product  {:db/id (c/parse-long product-id)}})])))

  (update-product [this]
    (let [{:keys [product-id store-id]} (get-route-params this)
          {:keys [selected-category-seq]} (om/get-state this)
          product (input-product this)
          skus (input-skus this product)
          selected-section (selected-section-entity this)
          [top-category sub-category] selected-category-seq
          validation (if (not-empty (:category/children top-category))
                       (v/validate ::category-seq {::select-top-category top-category
                                                   ::select-sub-category sub-category} form-elements)
                       (v/validate ::category-single {::select-top-category top-category} form-elements))]
      (debug "Validation: " validation)
      (when (nil? validation)
        (mixpanel/track "Store: Update product" {:product product})
        (msg/om-transact! this `[(store/update-product ~{:product    (cond-> product
                                                                             (not-empty skus)
                                                                             (assoc :store.item/skus skus)
                                                                             (some? selected-section)
                                                                             (assoc :store.item/section selected-section))
                                                         :product-id (c/parse-long product-id)
                                                         :store-id   (db/store-id->dbid this store-id)})
                                 :query/item
                                 :query/store]))
      (om/update-state! this (fn [st]
                               (-> st
                                   (dissoc :uploaded-photo)
                                   (assoc :input-validation validation))))))
  (create-product [this]
    (let [{:keys [store-id]} (get-route-params this)
          {:keys [selected-category-seq]} (om/get-state this)
          product (input-product this)
          skus (input-skus this product)
          selected-section (selected-section-entity this)
          [top-category sub-category] selected-category-seq
          validation (if (not-empty (:category/children top-category))
                       (v/validate ::category-seq {::select-top-category top-category
                                                   ::select-sub-category sub-category} form-elements)
                       (v/validate ::category-single {::select-top-category top-category} form-elements))]
      (when (nil? validation)
        (mixpanel/track "Store: Create new product" {:product product})
        (msg/om-transact! this `[(store/create-product ~{:product  (cond-> product
                                                                           (not-empty skus)
                                                                           (assoc :store.item/skus skus)
                                                                           (some? selected-section)
                                                                           (assoc :store.item/section selected-section))
                                                         :store-id (db/store-id->dbid this store-id)})
                                 :query/item
                                 :query/store]))
      (om/update-state! this (fn [st]
                               (-> st
                                   (dissoc :uploaded-photo)
                                   (assoc :input-validation validation))))))
  (remove-uploaded-photo [this index]
    (om/update-state! this update :uploaded-photos (fn [ps]
                                                     (into [] (remove nil? (assoc ps index nil))))))

  (select-category [this category-id old-seq]

    (let [{:query/keys [categories]} (om/props this)
          parent (last old-seq)
          category-id (c/parse-long-safe category-id)
          category (some #(when (= (:db/id %) category-id) %)
                         (if parent
                           (:category/children parent)
                           categories))]
      (debug "Category id to select: " {:id         category-id
                                        :category   category
                                        :old-seq    old-seq
                                        ;:parent     parent
                                        :categories (if parent (:category/children parent) categories)})
      (debug "Select category: " category)

      (om/update-state! this assoc :selected-category-seq (conj old-seq category) :input-validation nil)))

  (componentDidMount [this]
    (let [{:keys [product store]} (om/get-computed this)
          {:store.item/keys [photos skus]} product
          sku-count (if (< 0 (count skus)) (count skus) 1)]
      (om/update-state! this assoc
                        :did-mount? true
                        :uploaded-photos (into [] (sort-by :store.item.photo/index photos))
                        :sku-count sku-count
                        :store-sections (mapv (fn [s]
                                                {:label (:store.section/label s)
                                                 :value (:db/id s)})
                                              (:store/sections store)))))
  (componentWillReceiveProps [this next-props]
    (debug "Component will receive props: " next-props)
    (when-not (= (:query/item next-props) (:query/item (om/props this)))
      (let [{:query/keys [item]} next-props
            {:store.item/keys [category]} item]
        (om/update-state! this assoc :selected-category-seq (category-seq this category)))))

  (initLocalState [this]
    (let [{:query/keys [item]} (om/props this)
          {:store.item/keys [category]} item]
      {:sku-count             1
       :selected-category-seq (category-seq this category)}))

  (render [this]
    (let [{:keys [uploaded-photos queue-photo did-mount? sku-count selected-section store-sections selected-category-seq input-validation]} (om/get-state this)
          {:query/keys [navigation current-route categories item]} (om/props this)
          {:keys [product-id store-id]} (get-route-params this)
          ;{:keys [product]} (om/get-computed this)
          product item
          {:store.item/keys [price photos skus description category]
           item-name        :store.item/name} product
          message-pending-fn (fn [m] (when m (msg/pending? m)))
          update-resp (msg/last-message this 'store/update-product)
          create-resp (msg/last-message this 'store/create-product)
          delete-resp (msg/last-message this 'store/delete-product)
          is-loading? (or (message-pending-fn update-resp) (message-pending-fn create-resp) (message-pending-fn delete-resp))
          [top-category sub-category subsub-category] selected-category-seq
          ]

      ;(debug "Categories: " categories)
      ;(debug "Selected category: " category)
      ;(debug "Selected category seq: " selected-category-seq)
      (debug "Selected " {:top top-category :sub sub-category :subsub subsub-category})
      ;(debug "Messages: " {:update update-resp
      ;                     :create create-resp
      ;                     :delete delete-resp})
      (dom/div
        {:id "sulo-edit-product"}
        (when (or (not did-mount?) is-loading?)
          (common/loading-spinner nil))
        ;(menu/breadcrumbs
        ;  nil
        ;  (menu/item nil (dom/a {:href (routes/url :store-dashboard/product-list (get-route-params this))}
        ;                        "Products"))
        ;  (menu/item nil (dom/span nil
        ;                           (if (is-new-product? this) "New" "Edit"))))

        (grid/row-column
          (css/add-class :go-back)
          (dom/a
            {:href (routes/url :store-dashboard/product-list (:route-params current-route))}
            (dom/span nil "Back to product list")))

        (dom/div
          nil
          (dom/div
            (css/add-class :section-title)
            (dom/h1 nil (if product-id "Edit product" "New product")))
          (dom/div
            (css/add-class :section-title)
            (dom/h2 nil "Photos"))
          (callout/callout
            nil
            (grid/row
              (->> (css/add-class :photo-section)
                   (grid/columns-in-row {:small 3 :medium 4 :large 5}))
              (map-indexed
                (fn [i p]
                  (let [{:store.item.photo/keys [photo]} p
                        photo-key (or (:public_id p) (:photo/id photo) p)]
                    (grid/column
                      nil
                      (when (some? photo-key)
                        (dom/div
                          nil
                          (photo/square {:photo-id       photo-key
                                         :transformation :transformation/thumbnail})
                          (button/delete
                            {:onClick #(.remove-uploaded-photo this i)}
                            (dom/span nil "remove")))))))
                uploaded-photos)
              (when (some? queue-photo)
                (grid/column
                  nil
                  (photo/square {:src    (:src queue-photo)
                                 :status :loading})))
              (when (and (nil? queue-photo) (> 5 (count (conj uploaded-photos))))
                (grid/column
                  nil
                  (dom/label
                    {:htmlFor (str "product-photo-" (count photos))
                     :onClick #(mixpanel/track "Store: Open product photo upload")}
                    (empty-photo-button)
                    (photo-uploader this (count photos))))))))

        (dom/div
          nil
          (dom/div
            (css/add-class :section-title)
            (dom/h2 nil "Details"))
          ;(grid/row
          ;  (->> (css/align :bottom)
          ;       (css/add-class :page-header))
          ;  (grid/column
          ;    (grid/column-size {:small 12 :medium 8})
          ;    (if (is-new-product? this)
          ;      (dom/h3 nil "New product")
          ;      (dom/div
          ;        nil
          ;        (dom/h3 nil (dom/span nil "Edit product - ") (dom/small nil item-name))))))
          (callout/callout
            nil
            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Title"))
              (grid/column
                nil
                (dom/input {:id           (get form-elements :input-name)
                            :type         "text"
                            :defaultValue item-name})))


            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Description"))
              (grid/column
                nil
                (quill/->QuillEditor (om/computed {:content     (f/bytes->str description)
                                                   :placeholder "What's your product like?"
                                                   :id          "product-edit"
                                                   :enable?     true}
                                                  {:on-editor-created #(om/update-state! this assoc :quill-editor %)}))))





            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Section"))
              (grid/column
                nil
                (sel/->SelectOne (om/computed {:value       (or selected-section {:value (get-in product [:store.item/section :db/id])
                                                                                  :label (get-in product [:store.item/section :store.section/label])})
                                               :options     store-sections
                                               :creatable?  true
                                               :placeholder "Select section..."}
                                              {:on-change (fn [p]
                                                            (om/update-state! this (fn [st]
                                                                                     (-> st
                                                                                         (assoc :selected-section p)
                                                                                         (update :store-sections conj p))))
                                                            (debug "P: " p))}))
                (dom/p nil (dom/small nil "Use sections to organize your products within your store and give customer a better overview of your inventory when they visit."))))

            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Category"))
              (grid/column
                (grid/column-size {:small 12 :medium 3})
                (v/select
                  (cond->> {:value    (:db/id top-category "")
                            :id       (::select-top-category form-elements)
                            :onChange #(do (.select-category this (.-value (.-target %)) [])
                                           #?(:cljs
                                              (do
                                                (set! (.-value (utils/element-by-id (::select-sub-category form-elements))) "")
                                                ;(set! (.-value (utils/element-by-id (:select-subsub-category form-elements))) "")
                                                )))})
                  input-validation
                  (dom/option {:value "" :disabled true} "--- Select category ---")
                  (map (fn [c]
                         (dom/option {:value (:db/id c)} (:category/label c)))
                       categories))


                ;(callout/callout-small
                ;  (css/add-class :warning)
                ;  (dom/small nil "Categories are disabled while the feature is receiving more love from us to work perfectly for the public launch. Thank you for waiting!"))
                )
              (grid/column
                (grid/column-size {:small 12 :medium 3})
                (v/select
                  (cond->>
                    {:value    (:db/id sub-category "")
                     :id       (::select-sub-category form-elements)
                     :onChange #(.select-category this (.-value (.-target %)) [top-category])}
                    (or (nil? top-category) (empty? (:category/children top-category)))
                    (css/add-class :hide))
                  input-validation
                  (dom/option {:value "" :disabled true} "--- Select category ---")
                  (map (fn [c]
                         (dom/option {:value (:db/id c)} (:category/label c)))
                       (:category/children top-category))))

              ;(grid/column
              ;  (grid/column-size {:small 12 :medium 3})
              ;  (dom/select
              ;    (cond->>
              ;      {:defaultValue (:db/id subsub-category "")
              ;       :id           (:select-subsub-category form-elements)
              ;       :onChange     #(.select-category this (.-value (.-target %)) [top-category sub-category])}
              ;      (or (nil? sub-category) (empty? (:category/children sub-category)))
              ;      (css/add-class :hide))
              ;    (dom/option {:value "" :disabled true} "--- Select category ---")
              ;    (map (fn [c]
              ;           (dom/option {:value (:db/id c)} (:category/label c)))
              ;         (:category/children sub-category))))
              )
            (grid/row
              nil
              (grid/column
                (->> (grid/column-size {:small 12 :medium 10})
                     (grid/column-offset {:medium 2}))
                (dom/p nil (dom/small nil "Add a category to your products to let customers find them on the SULO marketplace."))))))




        (dom/div
          nil

          (dom/div
            (css/add-class :section-title)
            (dom/h2 nil "Inventory & price"))

          (callout/callout
            nil
            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Price in CAD"))
              (grid/column
                nil
                (grid/row
                  nil

                  (grid/column
                    nil
                    (dom/input {:id           (get form-elements :input-price)
                                :type         "number"
                                :step         "0.01"
                                :min          0
                                :max          "99999999.99"
                                :defaultValue (or price "0.00")}))

                  ;(grid/column
                  ;  nil
                  ;  (dom/select {:defaultValue "usd"}
                  ;              (dom/option {:value "usd"} "USD")))
                  )))
            (if (< 1 sku-count)
              (dom/div
                nil
                (map
                  (fn [index]
                    (let [sku (get skus index)
                          {:store.item.sku/keys [inventory variation]} sku
                          {:store.item.sku.inventory/keys [type value]} inventory
                          inventory-value (when value (name value))]
                      (grid/row
                        (css/add-class :input-sku-group)
                        (label-column
                          nil
                          (dom/label nil "Variation"))
                        (grid/column
                          nil
                          (grid/row
                            nil
                            (grid/column
                              nil
                              (dom/input
                                {:type         "text"
                                 :id           (str (:input-sku-value form-elements) index)
                                 :defaultValue (or variation "")}))
                            (grid/column
                              (css/add-class :shrink)
                              (dom/select {:defaultValue (or inventory-value "in-stock")
                                           :id           (str (:input-sku-inventory form-elements) index)}
                                          (dom/option {:value "in-stock"} "In stock")
                                          (dom/option {:value "out-of-stock"} "Out of stock")
                                          (dom/option {:value "limited"} "Limited")))
                            (grid/column
                              (css/add-class :shrink)
                              (button/delete
                                {:onClick #(om/update-state! this update :sku-count dec)}
                                (dom/span nil "remove"))))))))
                  (range sku-count)))
              (dom/div
                nil
                (grid/row
                  nil
                  (label-column nil
                                (dom/label nil "Availability"))
                  (grid/column
                    nil
                    (let [value (get-in (first skus) [:store.item.sku/inventory :store.item.sku.inventory/value])
                          inventory-value (if value (name value) "in-stock")]
                      (dom/select {:defaultValue inventory-value
                                   :id           (:input-main-inventory form-elements)}
                                  (dom/option {:value "in-stock"} "In stock")
                                  (dom/option {:value "out-of-stock"} "Out of stock")
                                  (dom/option {:value "limited"} "Limited")))))))
            (grid/row
              nil
              (label-column nil)
              (grid/column
                nil
                (button/store-setting-default
                  {:onClick #(do
                              (mixpanel/track "Store: Add product variation")
                              (om/update-state! this update :sku-count inc))}
                  (dom/span nil "Add variation..."))))))

        (grid/row
          (css/add-classes [:expanded :collapse])
          (grid/column
            (->> (css/add-class :action-buttons)
                 (css/text-align :left))
            (when-not (is-new-product? this)
              (dom/a {:classes ["button hollow alert"]
                      :onClick #(.delete-product this)}
                     (dom/span nil "Delete product"))))
          (grid/column
            (css/add-class :action-buttons)
            (button/store-navigation-secondary
              {:href    (routes/url :store-dashboard/product-list {:store-id store-id})
               :onClick #(mixpanel/track "Store: Cancel edit product")}
              (dom/span nil "Cancel"))
            (button/store-navigation-cta
              {
               :onClick #(when-not is-loading?
                          (if (is-new-product? this)
                            (.create-product this)
                            (.update-product this)))}
              (if is-loading?
                (dom/i {:classes ["fa fa-spinner fa-spin"]})
                (dom/span nil "Save product")))))
        (when (some? input-validation)
          (grid/row-column
            (css/text-align :right)
            (dom/p (css/add-class :text-alert) (dom/small nil "You have errors that need to be fixed before saving."))))
        (grid/row-column
          (css/add-class :go-back)
          (dom/a
            {:href (routes/url :store-dashboard/product-list (:route-params current-route))}
            (dom/span nil "Back to product list")))))))

(def ->ProductEditForm (om/factory ProductEditForm))
