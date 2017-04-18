(ns eponai.common.ui.store.product-edit-form
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.om-quill :as quill]
    [eponai.common.format :as f]
    [eponai.common.ui.common :as common]
    [eponai.client.parser.message :as msg]
    [eponai.common.ui.elements.css :as css]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    #?(:cljs
       [eponai.client.ui.photo-uploader :as pu])
    #?(:cljs
       [eponai.web.utils :as utils])
    [eponai.client.routes :as routes]
    [eponai.common :as c]
    [eponai.common.database :as db]))

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

(defn input-product [component]
  #?(:cljs
     (let [{:keys [uploaded-photo quill-editor]} (om/get-state component)
           {:keys [input-price input-name input-sku-price input-sku-value input-sku-quantity]} form-elements]
       {:name        (utils/input-value-or-nil-by-id input-name)
        :price       (utils/input-value-or-nil-by-id input-price)
        :currency    "CAD"
        :photo       uploaded-photo
        :description (quill/get-contents quill-editor)})))

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
                                                       :product {:db/id (c/parse-long product-id)}})])))

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
          is-loading? (or (message-pending-fn update-resp) (message-pending-fn create-resp) (message-pending-fn delete-resp))]
      (dom/div #js {:id "sulo-edit-product"}
        (when (or (not did-mount?) is-loading?)
          (common/loading-spinner nil))
        (my-dom/div
          (->> (css/grid-row))
          (my-dom/div
            (css/grid-column)
            (dom/h2 nil item-name " - " (dom/small nil product-id)))
          (my-dom/div
            (->> (css/grid-column)
                 (css/text-align :right))
            (dom/a #js {:className "button hollow alert"
                        :onClick   #(.delete-product this)} "Delete")))
        (my-dom/div
          (->> (css/grid-row)
               css/grid-column)
          (dom/div #js {:className "callout transparent"}
            (dom/h4 nil "Images")
            (my-dom/div
              (->> (css/grid-row)
                   (css/add-class :photo-section)
                   (css/grid-row-columns {:small 2 :medium 3 :large 4}))
              (my-dom/div
                (->> (css/grid-column))
                (if-let [photo-url (or (:location uploaded-photo) (:photo/path (first photos)))]
                  (photo/square {:src photo-url})
                  (if-let [queue-url queue-photo]
                    (photo/with-overlay nil (photo/square {:src queue-url}) (dom/i #js {:className "fa fa-spinner fa-spin fa-2x"}))
                    (dom/label #js {:htmlFor "file" :className "button secondary hollow expanded upload-button"}
                               ;(if loading?
                               ;  (dom/i #js {:className "fa fa-spinner fa-spin fa-2x"}))
                               (dom/div nil
                                 (dom/i #js {:className "fa fa-plus fa-3x"})))))
                (when did-mount?
                  #?(:cljs
                     (pu/->PhotoUploader (om/computed
                                           photo-upload
                                           {:on-photo-queue  (fn [img-result]
                                                               ;(debug "Got photo: " photo)
                                                               (om/update-state! this assoc :queue-photo img-result :uploaded-photo nil))
                                            :on-photo-upload (fn [photo]
                                                               (om/update-state! this assoc :uploaded-photo photo :queue-photo nil))}))))))))

        (my-dom/div (->> (css/grid-row)
                         (css/grid-column))
                    (dom/div #js {:className "callout transparent"}
                      (dom/h4 nil (dom/span nil "Details"))

                      (my-dom/div (->> (css/grid-row)
                                       (css/grid-column))
                                  (dom/label nil "Title")
                                  (my-dom/input {:id           (get form-elements :input-name)
                                                 :type         "text"
                                                 :defaultValue (or item-name "")}))
                      (my-dom/div (->> (css/grid-row)
                                       (css/grid-column))
                                  (dom/label nil "Description")
                                  (quill/->QuillEditor (om/computed {:content (f/bytes->str description)
                                                                     :placeholder "What's your product like?"}
                                                                    {:on-editor-created #(om/update-state! this assoc :quill-editor %)})))

                      ;(my-dom/div (->> (css/grid-row))
                      ;            (my-dom/div
                      ;              (->> (css/grid-column)
                      ;                   (css/grid-column-size {:medium 3}))
                      ;              (dom/label nil "Price")
                      ;              (my-dom/input {:id           (get form-elements :input-price)
                      ;                             :type         "number"
                      ;                             :step         "0.01"
                      ;                             :min          0
                      ;                             :max          "99999999.99"
                      ;                             :defaultValue (or price "")})))
                      ))
        (my-dom/div (->> (css/grid-row)
                         (css/grid-column))
                    (dom/div #js {:className "callout transparent"}
                      (dom/h4 nil (dom/span nil "Pricing"))
                      (my-dom/div (->> (css/grid-row))
                                  (my-dom/div
                                    (->> (css/grid-column))
                                    (dom/label nil "Price")
                                    (my-dom/input {:id           (get form-elements :input-price)
                                                   :type         "number"
                                                   :step         "0.01"
                                                   :min          0
                                                   :max          "99999999.99"
                                                   :defaultValue (or price "")}))
                                  (my-dom/div
                                    (->> (css/grid-column))
                                    ;(dom/label nil "On Sale")
                                    ;(my-dom/input {:id           (get form-elements :input-price)
                                    ;               :type         "checkbox"
                                    ;               :step         "0.01"
                                    ;               :min          0
                                    ;               :max          "99999999.99"
                                    ;               :defaultValue (or price "")})
                                    )
                                  (my-dom/div
                                    (->> (css/grid-column))
                                    ;(dom/label nil "Sale Price")
                                    ;(my-dom/input {:id           (get form-elements :input-price)
                                    ;               :type         "number"
                                    ;               :step         "0.01"
                                    ;               :min          0
                                    ;               :max          "99999999.99"
                                    ;               :defaultValue (or price "")})
                                    )
                                  )))

        (my-dom/div (->> (css/grid-row)
                         (css/grid-column))
                    (dom/div #js {:className "callout transparent"}
                      (dom/h4 nil (dom/span nil "Inventory"))
                      (my-dom/div
                        (->> (css/grid-row))
                        (my-dom/div
                          (css/grid-column)
                          (dom/label nil "Variation"))
                        (my-dom/div
                          (css/grid-column)
                          (dom/label nil "Quantity")))
                      (map-indexed
                        (fn [i sku]
                          (let [{:store.item.sku/keys [price value quantity]} sku]
                            (my-dom/div
                              (->> {:key (str i)
                                    :id (str (:store.item.sku/uuid sku))}
                                   (css/grid-row)
                                   (css/add-class :input-sku-group))
                              (my-dom/div
                                (css/grid-column)
                                (my-dom/input
                                  (->> {:type         "text"
                                        :defaultValue (or value "")}
                                       (css/add-class :input-sku-value))))
                              (my-dom/div
                                (css/grid-column)
                                (my-dom/input
                                  (->> {:type         "number"
                                        :defaultValue (or quantity "")
                                        :placeholder  "Unlimited"}
                                       (css/add-class :input-sku-quantity)))))))
                        skus)))
        (my-dom/div (->> (css/grid-row)
                         (css/grid-column))
                    (dom/div nil
                      (dom/a #js {:className "button"
                                  :onClick   #(when-not is-loading?
                                               (cond (some? product-id)
                                                     (.update-product this)
                                                     (= action "create")
                                                     (.create-product this)))}
                             (if is-loading?
                               (dom/i #js {:className "fa fa-spinner fa-spin"})
                               (dom/span nil "Save")))
                      (dom/a #js {:href      (routes/url :store-dashboard/product-list {:store-id store-id})
                                  :className "button hollow"} (dom/span nil "Cancel"))))))))

(def ->ProductEditForm (om/factory ProductEditForm))
