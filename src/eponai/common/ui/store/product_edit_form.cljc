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
    [taoensso.timbre :refer [debug]]
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
  {:input-price        "input-price"
   :input-on-sale?     "input-on-sale?"
   :input-sale-price   "input-sale-price"
   :input-name         "input-name"
   :input-desc         "input-desc"

   :input-sku-value    "input-sku-value"
   :input-sku-price    "input-sku-price"
   :input-sku-quantity "input-sku-quantity"

   :input-sku-group    "input-sku-group"})

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
     (let [{:keys [uploaded-photo quill-editor]} (om/get-state component)
           {:keys [input-price input-name input-sku-price input-sku-value input-sku-quantity]} form-elements]
       {:store.item/name        (utils/input-value-or-nil-by-id input-name)
        :store.item/price       (utils/input-value-or-nil-by-id input-price)
        :store.item/currency    "CAD"
        :store.item/photos      [uploaded-photo]
        :store.item/description (quill/get-HTML quill-editor)})))

(defn input-skus [product]
  #?(:cljs
     (let [{:keys [input-price input-name input-sku-price input-sku-value input-sku-quantity]} form-elements]
       (map (fn [el]
              (let [element-id (.-id el)
                    value (utils/first-input-value-by-class el input-sku-value)
                    quantity (utils/first-input-value-by-class el input-sku-quantity)]
                (cond-> {:id    (if (some? element-id)
                                  (f/str->uuid element-id)
                                  (db/squuid))
                         :value value
                         :price (:price product)}
                        (some? quantity)
                        (assoc :quantity quantity))))
            (utils/elements-by-class (:input-sku-group form-elements))))))

(defn label-column [opts & content]
  (grid/column
    (grid/column-size {:small 12 :large 2} opts)
    content))

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
    (let [{:keys [product-id]} (get-route-params this)]
      (msg/om-transact! this `[(store/delete-product ~{:product {:db/id (c/parse-long product-id)}})])))

  (update-product [this]
    (let [{:keys [product-id store-id]} (get-route-params this)
          product (input-product this)
          skus (input-skus product)]
      (msg/om-transact! this `[(store/update-product ~{:product    (cond-> product
                                                                           (not-empty skus)
                                                                           (assoc :skus skus))
                                                       :product-id product-id
                                                       :store-id   store-id})
                               :query/store])
      (om/update-state! this dissoc :uploaded-photo)))
  (create-product [this]
    (let [{:keys [store-id]} (get-route-params this)
          product (input-product this)
          skus (input-skus product)]
      (msg/om-transact! this `[(store/create-product ~{:product  (cond-> product
                                                                         (not-empty skus)
                                                                         (assoc :skus skus))
                                                       :store-id store-id})
                               :query/store])
      (om/update-state! this dissoc :uploaded-photo)))
  (componentDidMount [this]
    (om/update-state! this assoc :did-mount? true))

  (render [this]
    (let [{:keys [uploaded-photo queue-photo variations? did-mount?]} (om/get-state this)
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
                (dom/h3 nil (dom/span nil "Edit product - ") (dom/small nil (str item-name " asdskdfbds kfjs kfjskf bdbf jshdb jfhb sdjfhb sdjbfjsh "))))))
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
                            :defaultValue (or item-name "")})))

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
              (grid/column
                nil
                (grid/row
                  nil
                  (label-column
                    nil
                    (dom/label nil "Price"))
                  (grid/column
                    nil
                    (dom/input {:id           (get form-elements :input-price)
                                :type         "number"
                                :step         "0.01"
                                :min          0
                                :max          "99999999.99"
                                :defaultValue (or price "")}))))
              (grid/column
                nil
                (grid/row
                  nil
                  (label-column
                    nil
                    (dom/label nil "Price"))
                  (grid/column
                    nil
                    (dom/select {:defaultValue "usd"}
                                (dom/option {:value "usd"} "USD")))))))

          (callout/callout
            nil
            (callout/header nil "Images")

            (grid/row
              (->> (css/add-class :photo-section)
                   (grid/columns-in-row {:small 3 :medium 4 :large 5}))
              (grid/column
                nil
                (if-let [photo-url (or (:location uploaded-photo) (:photo/path (first photos)))]
                  (photo/square {:src photo-url})
                  (if-let [queue-url queue-photo]
                    (photo/with-overlay
                      nil
                      (photo/square {:src queue-url})
                      (dom/i {:classes ["fa fa-spinner fa-spin fa-2x"]}))
                    (dom/label
                      {:htmlFor "file" :classes ["button secondary hollow expanded upload-button"]}
                      ;(if loading?
                      ;  (dom/i #js {:className "fa fa-spinner fa-spin fa-2x"}))
                      (dom/div nil
                               (dom/i {:classes ["fa fa-plus fa-3x"]})))))
                (when did-mount?
                  #?(:cljs
                     (pu/->PhotoUploader (om/computed
                                           photo-upload
                                           {:on-photo-queue  (fn [img-result]
                                                               ;(debug "Got photo: " photo)
                                                               (om/update-state! this assoc :queue-photo img-result :uploaded-photo nil))
                                            :on-photo-upload (fn [photo]
                                                               (om/update-state! this assoc :uploaded-photo photo :queue-photo nil))})))))))


          (callout/callout
            nil
            (callout/header nil "Inventory")
            (grid/row
              nil
              (grid/column
                nil
                (dom/label nil "Variation"))
              (grid/column
                nil
                (dom/label nil "Quantity")))
            (map-indexed
              (fn [i sku]
                (let [{:store.item.sku/keys [price value quantity]} sku]
                  (grid/row
                    (->> {:id (str (:store.item.sku/uuid sku))}
                         (css/add-class :input-sku-group))
                    (grid/column
                      nil
                      (dom/input
                        (->> {:type         "text"
                              :defaultValue (or value "")}
                             (css/add-class :input-sku-value))))
                    (grid/column
                      nil
                      (dom/input
                        (->> {:type         "number"
                              :defaultValue (or quantity "")
                              :placeholder  "Unlimited"}
                             (css/add-class :input-sku-quantity)))))))
              skus)))
        (grid/row-column
          (css/text-align :right)
          (dom/a
            (css/button-hollow {:href (routes/url :store-dashboard/product-list {:store-id store-id})})
            (dom/span nil "Cancel"))
          (dom/a
            (->> {:onClick #(when-not is-loading?
                             (cond (some? product-id)
                                   (.update-product this)
                                   (= action "create")
                                   (.create-product this)))}
                 (css/button))
            (if is-loading?
              (dom/i {:classes ["fa fa-spinner fa-spin"]})
              (dom/span nil "Save"))))))))

(def ->ProductEditForm (om/factory ProductEditForm))
