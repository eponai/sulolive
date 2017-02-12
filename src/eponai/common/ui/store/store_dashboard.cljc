(ns eponai.common.ui.store.store-dashboard
  (:require
    [eponai.common.ui.om-quill :as quill]
    [eponai.client.parser.message :as msg]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.common :as common]
    #?(:cljs [eponai.web.utils :as utils])
    #?(:cljs [eponai.client.ui.photo-uploader :as pu])
    [eponai.client.routes :as routes]
    [eponai.common.ui.dom :as my-dom]
    [om.dom :as dom]
    [om.next :as om #?(:clj :refer :cljs :refer-macros) [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common :as c]
    [eponai.common.database :as db]
    [eponai.common.format :as f]))

(def form-elements
  {:input-price        "input-price"
   :input-on-sale?     "input-on-sale?"
   :input-sale-price   "input-sale-price"
   :input-name         "input-name"
   :input-desc         "input-desc"

   :input-sku-value    "input-sku-value"
   :input-sku-price    "input-sku-price"
   :input-sku-quantity "input-sku-quantity"})

(defn store-orders [component store opts]
  (my-dom/div (->> (css/grid-row)
                   css/grid-column)
              (dom/div nil "Thes are the orders")))

#?(:cljs
   (defn get-element-by-id [id]
     (.getElementById js/document id)))

#?(:cljs (defn get-input-value [id]
           (when-let [el (get-element-by-id id)]
             (.-value el))))

(defn str->json [s]
  #?(:cljs (cljs.reader/read-string s)
     :clj  (clojure.data.json/read-str s :key-fn keyword)))

(defn product-edit-form [component & [product]]
  (let [{:keys [uploaded-photo queue-photo]} (om/get-state component)
        {:keys [proxy/photo-upload]} (om/props component)
        {:store.item/keys [price photos skus description]
         item-name        :store.item/name} product
        message-pending-fn (fn [m] (when m (msg/pending? m)))
        update-resp (msg/last-message component 'store/update-product)
        create-resp (msg/last-message component 'store/create-product)
        delete-resp (msg/last-message component 'store/delete-product)
        is-loading? (or (message-pending-fn update-resp) (message-pending-fn create-resp) (message-pending-fn delete-resp))]
    (dom/div nil
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
                      :onClick   #(.delete-product component)} "Delete")))
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
                                (quill/->QuillEditor (om/computed {:content (f/bytes->str description)}
                                                                  {:on-editor-created #(om/update-state! component assoc :quill-editor %)})))
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
                                                 :defaultValue (or price "")})))))
      (my-dom/div (->> (css/grid-row)
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
                                                                     (om/update-state! component assoc :queue-photo img-result :uploaded-photo nil))
                                                  :on-photo-upload (fn [photo]
                                                                     (om/update-state! component assoc :uploaded-photo photo :queue-photo nil))})))))))

      (my-dom/div (->> (css/grid-row)
                       (css/grid-column))
                  (dom/div #js {:className "callout transparent"}
                    (dom/h4 nil (dom/span nil "Inventory"))
                    (let [{:store.item.sku/keys [price value quantity]} (first skus)]
                      (my-dom/div (->> (css/grid-row))
                                  (my-dom/div
                                    (css/grid-column)
                                    (dom/label nil "Variation")
                                    (my-dom/input {:id           (get form-elements :input-sku-value)
                                                   :type         "text"
                                                   :defaultValue (or value "")}))
                                  (my-dom/div
                                    (css/grid-column)
                                    (dom/label nil "Price")
                                    (my-dom/input {:id           (get form-elements :input-sku-price)
                                                   :type         "number"
                                                   :defaultValue (or price "")}))
                                  (my-dom/div
                                    (css/grid-column)
                                    (dom/label nil "Quantity")
                                    (my-dom/input {:id           (get form-elements :input-sku-quantity)
                                                   :type         "number"
                                                   :defaultValue (or quantity "")}))))))
      (my-dom/div (->> (css/grid-row)
                       (css/grid-column))
                  (dom/div nil
                    (dom/a #js {:className "button hollow"} (dom/span nil "Cancel"))
                    (dom/a #js {:className "button"
                                :onClick   #(when-not is-loading? (.update-product component))}
                           (if is-loading?
                             (dom/i #js {:className "fa fa-spinner fa-spin"})
                             (dom/span nil "Save"))))))))

(defn products-list [component store]
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
                 (:store/items store))))))))

(defn find-product [store product-id]
  (let [product-id (c/parse-long product-id)]
    (some #(when (= (:db/id %) product-id) %)
          (:store/items store))))

(defn store-products [component store {:keys [product-id action] :as rp}]
  (if (or (= action "create") (some? product-id))
    (let [product-id (when (some? product-id)
                       (c/parse-long product-id))
          selected-product (some #(when (= (:db/id %) product-id) %) (:store/items store))]
      (product-edit-form component selected-product))
    (products-list component store)))

(defn store-stream [component store route-params]
  )

(defn get-route-params [component]
  (get-in (om/props component) [:query/current-route :route-params]))

(defui StoreDashboard
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/store [:store/uuid
                    :store/name
                    {:store/owners [{:store.owner/user [:user/email]}]}
                    :store/stripe
                    {:store/items [:store.item/name
                                   :store.item/description
                                   :store.item/price
                                   {:store.item/photos [:photo/path]}
                                   {:store.item/skus [:store.item.sku/price
                                                      :store.item.sku/quantity
                                                      :store.item.sku/value]}]}
                    :store/collections]}
     :query/current-route
     :query/messages
     #?(:cljs
        {:proxy/photo-upload (om/get-query pu/PhotoUploader)})
     ])
  Object
  (initLocalState [_]
    {:selected-tab :products})
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
                           product {:name        (get-input-value input-name)
                                    :price       (get-input-value input-price)
                                    :currency    "CAD"
                                    :photo       uploaded-photo
                                    :description (js/JSON.stringify (quill/get-contents quill-editor))}]

                       (cond (some? product-id)
                             (msg/om-transact! this `[(store/update-product ~{:product    product
                                                                              :product-id product-id
                                                                              :store-id   store-id})
                                                      :query/store])

                             (= action "create")
                             (msg/om-transact! this `[(store/create-product
                                                        ~{:product  (assoc product :id (db/squuid)
                                                                                   :skus [{:id       (db/squuid)
                                                                                           :price    (get-input-value input-sku-price)
                                                                                           :value    (get-input-value input-sku-value)
                                                                                           :quantity (get-input-value input-sku-quantity)
                                                                                           :type     (if (get-input-value input-sku-value)
                                                                                                       :store.item.sku.type/finite
                                                                                                       :store.item.sku.type/infinite)}])
                                                          :store-id store-id})
                                                      :query/store]))
                       (om/update-state! this dissoc :uploaded-photo))))

  (componentDidUpdate [this _ _]
    #?(:cljs
       (when-let [action-finished (some #(when (msg/final? (msg/last-message this %)) %)
                                        ['store/update-product
                                         'store/create-product
                                         'store/delete-product])]
         (msg/clear-one-message! this action-finished)
         (routes/set-url! this :store-dashboard/product-list {:store-id (get-in (om/props this) [:query/store :db/id])}))))

  (render [this]
    (let [{:keys [proxy/navbar query/store query/current-route]} (om/props this)
          {:keys [route route-params]} current-route]
      (dom/div #js {:id "sulo-my-store" :className "sulo-page"}
        (common/page-container
          {:navbar navbar}
          (my-dom/div
            (->> (css/grid-row))

            (my-dom/div
              (->> (css/grid-column))
              (menu/horizontal
                (css/add-class :store-nav)
                ;(menu-item "/products" "Products" (= dashboard-option "products"))
                ;(menu-item "/orders" "Orders" (= dashboard-option "orders"))
                (menu/item-tab {:active? (= route :store-dashboard/product-list)
                                :href    (routes/url :store-dashboard/product-list {:store-id (:db/id store)})}
                               "Products")
                (menu/item-tab {:active? (= route :store-dashboard/orders)
                                :href    (routes/url :store-dashboard/orders {:store-id (:db/id store)})}
                               "Orders"))))
          (condp = route
            :store-dashboard/orders (store-orders this store route-params)
            :store-dashboard/stream (store-stream this store route-params)
            :store-dashboard/product-list (products-list this store)
            :store-dashboard/create-product (product-edit-form this)
            :store-dashboard/product (product-edit-form this (find-product store (:product-id route-params)))
            nil))))))

(def ->StoreDashboard (om/factory StoreDashboard))
