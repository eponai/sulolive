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

#?(:cljs
   (defn get-element-by-id [id]
     (.getElementById js/document id)))

#?(:cljs (defn get-input-value [id]
           (when-let [el (get-element-by-id id)]
             (.-value el))))

(defui ProductEditForm
  static om/IQuery
  (query [_]
    [:query/current-route
     :query/messages
     #?(:cljs
        {:proxy/photo-upload (om/get-query pu/PhotoUploader)})])
  Object
  (componentDidUpdate [this _ _]
    #?(:cljs
       (when-let [action-finished (some #(when (msg/final? (msg/last-message this %)) %)
                                        ['store/update-product
                                         'store/create-product
                                         'store/delete-product])]
         (msg/clear-one-message! this action-finished)
         ;(routes/set-url! this :store-dashboard/product-list {:store-id (:store-id (get-route-params this))})
         )))
  #?(:cljs
     (delete-product
       [this]
       (let [{:keys [product-id]} (get-route-params this)]
         (msg/om-transact! this `[(store/delete-product ~{:product {:db/id (c/parse-long product-id)}})]))))

  #?(:cljs
     (update-product [this]
                     (let [{:keys [product-id store-id action]} (get-route-params this)
                           {:keys [uploaded-photo quill-editor]} (om/get-state this)
                           {:keys [input-price input-name input-sku-price input-sku-value input-sku-quantity]} form-elements
                           product {:name        (utils/input-value-by-id input-name)
                                    :price       (utils/input-value-by-id input-price)
                                    :currency    "CAD"
                                    :photo       uploaded-photo
                                    :description (js/JSON.stringify (quill/get-contents quill-editor))}
                           skus (map (fn [el]
                                       (let [
                                             value (utils/first-input-value-by-class el input-sku-value)
                                             quantity (utils/first-input-value-by-class el input-sku-quantity)]
                                         (debug "Quantity: " quantity)
                                         (cond-> {:id       (db/squuid)
                                                  :value    value}
                                                 (some? quantity)
                                                 (assoc :quantity quantity))))
                                     (utils/elements-by-class (:input-sku-group form-elements)))]

                       (cond (some? product-id)
                             (msg/om-transact! this `[(store/update-product ~{:product    product
                                                                              :product-id product-id
                                                                              :store-id   store-id})
                                                      :query/store])

                             (= action "create")
                             (msg/om-transact! this `[(store/create-product
                                                        ~{:product  (assoc product :id (db/squuid)
                                                                                   :skus skus)
                                                          :store-id store-id})
                                                      :query/store]))
                       (om/update-state! this dissoc :uploaded-photo))))
  (render [this]
    (let [{:keys [uploaded-photo queue-photo variations?]} (om/get-state this)
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
        #?(:cljs (when is-loading?
                   (common/loading-spinner nil))
           :clj  (common/loading-spinner nil))
        (my-dom/div
          (->> (css/grid-row))
          (my-dom/div
            (css/grid-column)
            (dom/h2 nil "Edit Product - " (dom/small nil item-name)))
          (my-dom/div
            (->> (css/grid-column)
                 (css/text-align :right))
            (dom/a #js {:className "button hollow"
                        :onClick   #(.delete-product this)} "Delete")))
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
                      (my-dom/div (->> (css/grid-row))
                                  (my-dom/div
                                    (->> (css/grid-column)
                                         (css/grid-column-size {:medium 3}))
                                    (dom/label nil "Price")
                                    (my-dom/input {:id           (get form-elements :input-price)
                                                   :type         "number"
                                                   :step         "0.01"
                                                   :min          0
                                                   :max          "99999999.99"
                                                   :defaultValue (or price "")})))
                      (my-dom/div (->> (css/grid-row)
                                       (css/grid-column))
                                  (dom/label nil "Description")
                                  (quill/->QuillEditor (om/computed {:content (f/bytes->str description)}
                                                                    {:on-editor-created #(om/update-state! this assoc :quill-editor %)})))))

        (my-dom/div
          (->> (css/grid-row)
               css/grid-column)
          (dom/div #js {:className "callout transparent"}
            (dom/h4 nil "Images")
            (my-dom/div
              (->> (css/grid-row)
                   (css/add-class :photo-section))
              (my-dom/div
                (->> (css/grid-column)
                     (css/grid-column-size {:small 12 :medium 4 :large 2}))
                (if-let [photo-url (or (:location uploaded-photo) (:photo/path (first photos)))]
                  (photo/square {:src photo-url})
                  (if-let [queue-url queue-photo]
                    (photo/with-overlay nil (photo/square {:src queue-url}) (dom/i #js {:className "fa fa-spinner fa-spin fa-2x"}))
                    (dom/label #js {:htmlFor "file" :className "button secondary hollow expanded upload-button"}
                               ;(if loading?
                               ;  (dom/i #js {:className "fa fa-spinner fa-spin fa-2x"}))
                               (dom/div nil
                                 (dom/i #js {:className "fa fa-plus fa-3x"})))))
                #?(:cljs
                   (pu/->PhotoUploader (om/computed
                                         photo-upload
                                         {:on-photo-queue  (fn [img-result]
                                                             ;(debug "Got photo: " photo)
                                                             (om/update-state! this assoc :queue-photo img-result :uploaded-photo nil))
                                          :on-photo-upload (fn [photo]
                                                             (om/update-state! this assoc :uploaded-photo photo :queue-photo nil))})))))))

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
                          (->> (css/grid-column)
                               (css/text-align :right))
                          (dom/label nil "Quantity")))
                      (let [{:store.item.sku/keys [price value quantity]} (first skus)]
                        (my-dom/div
                          (->> (css/grid-row)
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
                                    :defaultValue (or quantity "")}
                                   (css/add-class :input-sku-quantity)
                                   (css/text-align :right))))))))
        (my-dom/div (->> (css/grid-row)
                         (css/grid-column))
                    (dom/div nil
                      (dom/a #js {:className "button hollow"} (dom/span nil "Cancel"))
                      (dom/a #js {:className "button"
                                  :onClick   #(when-not is-loading? (.update-product this))}
                             (if is-loading?
                               (dom/i #js {:className "fa fa-spinner fa-spin"})
                               (dom/span nil "Save")))))))))

(def ->ProductEditForm (om/factory ProductEditForm))
