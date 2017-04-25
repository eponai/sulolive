(ns eponai.common.ui.store.product-edit-form
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.photo :as photo]
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
    [eponai.common.database :as db]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.menu :as menu]))

(def form-elements
  {:input-price          "input-price"
   :input-on-sale?       "input-on-sale?"
   :input-sale-price     "input-sale-price"
   :input-name           "input-name"
   :input-desc           "input-desc"
   :input-main-inventory "input-main-inventory"

   :input-sku-value      "input-sku-value-"
   :input-sku-inventory  "input-sku-inventory-"

   :input-sku-group      "input-sku-group"})

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
     (let [{:keys [uploaded-photos quill-editor]} (om/get-state component)
           {:keys [input-price input-name input-sku-price input-sku-value input-sku-quantity]} form-elements]
       {:store.item/name        (utils/input-value-or-nil-by-id input-name)
        :store.item/price       (utils/input-value-or-nil-by-id input-price)
        :store.item/currency    "CAD"
        :store.item/photos      uploaded-photos
        :store.item/description (quill/get-HTML quill-editor)})))

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
                      (assoc sku :store.item.sku/uuid (:store.item.sku/uuid old-sku))
                      sku)))
                (range sku-count)))
         (let [inventory (utils/selected-value-by-id input-main-inventory)]
           [{:store.item.sku/inventory {:store.item.sku.inventory/value (keyword inventory)}}])))))

(defn label-column [opts & content]
  (grid/column
    (grid/column-size {:small 12 :large 2} opts)
    content))

(defn photo-uploader [component index]
  (let [{:keys [did-mount?]} (om/get-state component)
        {:proxy/keys [photo-upload]} (om/props component)]
    
    (when did-mount?
      #?(:cljs
         (pu/->PhotoUploader (om/computed
                               photo-upload
                               {:on-photo-queue  (fn [img-result]
                                                   ;(debug "Got photo: " photo)
                                                   (om/update-state! component assoc :queue-photo {:location  img-result
                                                                                                   :in-queue? true}))
                                :on-photo-upload (fn [photo]
                                                   (om/update-state! component (fn [st]
                                                                                 (-> st
                                                                                     (dissoc :queue-photo)
                                                                                     (update :uploaded-photos conj photo)))))
                                :id              (str index)
                                :hide-label?     true}))))))

(defn empty-photo-button []
  (dom/div
    (css/add-class :empty-photo)
    (dom/div
      nil
      (dom/i {:classes ["fa fa-plus fa-4x"]}))))

(defui ProductEditForm
  static om/IQuery
  (query [_]
    [:query/current-route
     :query/messages
     #?(:cljs
        {:proxy/photo-upload (om/get-query pu/PhotoUploader)})])
  Object
  (componentDidUpdate [this _ _]
    (when-let [action-finished (some #(when (msg/final? (msg/last-message this %)) %)
                                     ['store/update-product
                                      'store/create-product
                                      'store/delete-product])]
      (msg/clear-one-message! this action-finished)
      (routes/set-url! this :store-dashboard/product-list {:store-id (:store-id (get-route-params this))})
      ))
  (delete-product [this]
    (let [{:keys [product-id store-id]} (get-route-params this)]
      (msg/om-transact! this `[(store/delete-product ~{:store-id store-id
                                                       :product  {:db/id (c/parse-long product-id)}})])))

  (update-product [this]
    (let [{:keys [product-id store-id]} (get-route-params this)
          product (input-product this)
          skus (input-skus this product)]
      (msg/om-transact! this `[(store/update-product ~{:product    (cond-> product
                                                                           (not-empty skus)
                                                                           (assoc :store.item/skus skus))
                                                       :product-id product-id
                                                       :store-id   store-id})
                               :query/store])
      (om/update-state! this dissoc :uploaded-photo)))
  (create-product [this]
    (let [{:keys [store-id]} (get-route-params this)
          product (input-product this)
          skus (input-skus this product)]
      (msg/om-transact! this `[(store/create-product ~{:product  (cond-> product
                                                                         (not-empty skus)
                                                                         (assoc :store.item/skus skus))
                                                       :store-id store-id})
                               :query/store])
      (om/update-state! this dissoc :uploaded-photo)))
  (remove-uploaded-photo [this index]
    (om/update-state! this update :uploaded-photos (fn [ps]
                                                     (into [] (remove nil? (assoc ps index nil))))))
  (componentDidMount [this]
    (let [{:keys [product]} (om/get-computed this)
          {:store.item/keys [photos skus]} product
          sku-count (if (< 0 (count skus)) (count skus) 1)]
      (om/update-state! this assoc
                        :did-mount? true
                        :uploaded-photos (into [] (sort-by :store.item.photo/index photos))
                        :sku-count sku-count)))
  (initLocalState [_]
    {:sku-count 1})

  (render [this]
    (let [{:keys [uploaded-photos queue-photo variations? did-mount? sku-count]} (om/get-state this)
          {:keys [product-id store-id action]} (get-route-params this)
          {:keys [proxy/photo-upload]} (om/props this)
          {:keys [product]} (om/get-computed this)
          {:store.item/keys [price photos skus description]
           item-name        :store.item/name} product
          message-pending-fn (fn [m] (when m (msg/pending? m)))
          update-resp (msg/last-message this 'store/update-product)
          create-resp (msg/last-message this 'store/create-product)
          delete-resp (msg/last-message this 'store/delete-product)
          is-loading? (or (message-pending-fn update-resp) (message-pending-fn create-resp) (message-pending-fn delete-resp))
          ]

      (dom/div
        {:id "sulo-edit-product"}
        (when (or (not did-mount?) is-loading?)
          (common/loading-spinner nil))
        (grid/row-column
          nil
          (menu/breadcrumbs
            nil
            (menu/item nil (dom/a {:href (routes/url :store-dashboard/product-list {:store-id store-id})}
                                  "Products"))
            (menu/item nil (dom/span nil
                                     (if (is-new-product? this) "New" "Edit")))))
        (grid/row
          (->> (css/align :bottom)
               (css/add-class :page-header))
          (grid/column
            (grid/column-size {:small 12 :medium 8})
            (if (is-new-product? this)
              (dom/h3 nil "New product")
              (dom/div
                nil
                (dom/h3 nil (dom/span nil "Edit product - ") (dom/small nil item-name)))))
          (grid/column
            (css/text-align :right)
            (when-not (is-new-product? this)
              (dom/a {:classes ["button hollow alert"]
                      :onClick #(.delete-product this)}
                     (dom/span nil "Delete")))))

        (grid/row-column
          nil
          (callout/callout
            nil
            (callout/header nil "Details")

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
                                                   :id          "product-edit"}
                                                  {:on-editor-created #(om/update-state! this assoc :quill-editor %)}))))

            (callout/header nil "Pricing")
            (grid/row
              nil
              (label-column
                nil
                (dom/label nil "Price"))
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
                                :defaultValue (or price "")}))

                  (grid/column
                    nil
                    (dom/select {:defaultValue "usd"}
                                (dom/option {:value "usd"} "USD")))))))

          (callout/callout
            nil
            (callout/header nil "Photos")

            (grid/row
              (->> (css/add-class :photo-section)
                   (grid/columns-in-row {:small 3 :medium 4 :large 5}))
              (map-indexed
                (fn [i p]
                  (let [file-id (str "file-" i)
                        {:store.item.photo/keys [photo]} p
                        {:keys [in-queue?]} p
                        photo-url (or (:location p) (:photo/path photo) p)]
                    (grid/column
                      nil
                      ;(dom/label
                      ;  {:htmlFor file-id})
                      (when (some? photo-url)
                        (dom/div
                          nil
                          (if in-queue?
                            (photo/with-overlay
                              nil
                              (photo/square {:src photo-url})
                              (dom/i {:classes ["fa fa-spinner fa-spin"]}))
                            (photo/square {:src photo-url}))
                          (when-not in-queue?
                            (dom/a
                              (->>
                                {:onClick #(.remove-uploaded-photo this i)}
                                (css/button-hollow)) (dom/span nil "Remove")))))
                      ;(photo-uploader this i)
                      )))
                (if queue-photo
                  (conj uploaded-photos queue-photo)
                  uploaded-photos))
              (when (> 5 (count uploaded-photos))
                (grid/column
                  nil
                  ;(photo/product-photo nil)
                  (dom/label
                    {:htmlFor (str "file-" (count photos))}
                    (empty-photo-button)
                    (photo-uploader this (count photos)))))))


          (callout/callout
            nil
            (callout/header nil "Inventory")
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
                              (dom/a (->> {:onClick #(om/update-state! this update :sku-count dec)}
                                          (css/button-hollow)
                                          (css/add-class ::css/color-secondary)) (dom/span nil "X"))))))))
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
                (dom/a
                  (->> {:onClick #(om/update-state! this update :sku-count inc)}
                       (css/button-hollow)) (dom/span nil "Add variation..."))))))
        (grid/row-column
          (css/text-align :right)
          (dom/a
            (css/button-hollow {:href (routes/url :store-dashboard/product-list {:store-id store-id})})
            (dom/span nil "Cancel"))
          (dom/a
            (->> {
                  :onClick #(when-not is-loading?
                             (if (is-new-product? this)
                               (.create-product this)
                               (.update-product this)))}
                 (css/button))
            (if is-loading?
              (dom/i {:classes ["fa fa-spinner fa-spin"]})
              (dom/span nil "Save"))))))))

(def ->ProductEditForm (om/factory ProductEditForm))
