(ns eponai.common.ui.store.store-dashboard
  (:require
    [eponai.client.parser.message :as msg]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.product-item :as pi]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.product :as item]
    [eponai.common.ui.stream :as stream]
    #?(:cljs [eponai.web.utils :as utils])
    #?(:cljs [eponai.client.ui.photo-uploader :as pu])
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.format :as format]
    [om.dom :as dom]
    [om.next :as om #?(:clj :refer :cljs :refer-macros) [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.menu :as menu]
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
   :input-sku-quantity "input-sku-quantity"})

(defn route-store [store-id & [path]]
  (str "/store/" store-id "/dashboard" path))

(defn store-orders [component store opts]
  (my-dom/div (->> (css/grid-row)
                   css/grid-column)
              (dom/div nil "Thes are the orders")))

#?(:cljs (defn get-input-value [id]
           (when-let [el (.getElementById js/document id)]
             (.-value el))))

(defn product-edit-form [component & [product]]
  (let [{:keys [uploaded-photo queue-photo]} (om/get-state component)
        {:keys [proxy/photo-upload]} (om/props component)
        {:store.item/keys [price photos skus]
         item-name        :store.item/name} product
        message-pending-fn (fn [m] (when m (msg/pending? m)))
        update-resp (msg/last-message component 'store/update-product)
        create-resp (msg/last-message component 'store/create-product)
        delete-resp (msg/last-message component 'store/delete-product)
        is-loading? (or (message-pending-fn update-resp) (message-pending-fn create-resp) (message-pending-fn delete-resp))]
    (debug "State: " (om/get-state component))
    (debug "Messages: " {:update update-resp :create create-resp})
    (dom/div nil
      (when is-loading?
        (common/loading-spinner nil))
      (my-dom/div
        (->> (css/grid-row))
        (my-dom/div
          (css/grid-column)
          (dom/h2 nil "Edit Product - " (dom/small nil item-name)))
        (my-dom/div
          (->> (css/grid-column)
               (css/text-align :right))
          (dom/a #js {:className "button hollow"
                      :onClick #(.delete-product component)} "Delete")))

      (my-dom/div (->> (css/grid-row)
                       (css/grid-column))
                  ;(dom/h3 nil (dom/span nil "Details"))
                  (dom/div #js {:className "callout transparent"}

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
                                                                     (om/update-state! component assoc :uploaded-photo photo :queue-photo nil))})))))

                    (my-dom/div (->> (css/grid-row)
                                     (css/grid-column))
                                (dom/label nil "Name")
                                (my-dom/input {:id           (get form-elements :input-name)
                                               :type         "text"
                                               :defaultValue (or item-name "")}))
                    (my-dom/div (->> (css/grid-row)
                                     (css/grid-column))
                                (dom/label nil "Description")
                                (my-dom/input {:id           (get form-elements :input-desc)
                                               :type         "text"
                                               :defaultValue ""}))
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
                                                 :defaultValue (or price "")}))
                                ;(my-dom/div (css/grid-column)
                                ;            (dom/label nil "On Sale")
                                ;            (my-dom/input {:id   (get form-elements :input-on-sale?)
                                ;                           :type "checkbox"}))
                                ;(my-dom/div (css/grid-column)
                                ;            (dom/label nil "Sale Price")
                                ;            (my-dom/input {:id           (get form-elements :input-sale-price)
                                ;                           :className    "disabled"
                                ;                           :type         "number"
                                ;                           :disabled     true
                                ;                           :defaultValue (or price "")}))
                                )))
      (my-dom/div (->> (css/grid-row)
                       (css/grid-column))
                  ;(dom/h3 nil (dom/span nil "Details"))
                  (dom/div #js {:className "callout transparent"}
                    ;(map (fn [sku]
                    ;       (let [{:store.item.sku/keys [price value quantity]} sku]
                    ;         (my-dom/div (->> (css/grid-row))
                    ;                     (my-dom/div
                    ;                       (css/grid-column)
                    ;                       (dom/label nil "Variation")
                    ;                       (my-dom/input {:id           (get form-elements :input-sku-value)
                    ;                                      :type         "text"
                    ;                                      :defaultValue (or value "")}))
                    ;                     (my-dom/div
                    ;                       (css/grid-column)
                    ;                       (dom/label nil "Price")
                    ;                       (my-dom/input {:id           (get form-elements :input-sku-price)
                    ;                                      :type         "number"
                    ;                                      :defaultValue (or price "Unlimited")}))
                    ;                     (my-dom/div
                    ;                       (css/grid-column)
                    ;                       (dom/label nil "Quantity")
                    ;                       (my-dom/input {:id           (get form-elements :input-sku-quantity)
                    ;                                      :type         "text"
                    ;                                      :defaultValue (or quantity "")})))))
                    ;     skus)
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
                    :href      (route-store (:db/id store) "/products/create")} "Add product")))

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
                   (dom/tr nil
                           (dom/td nil
                                   (dom/input #js {:type "checkbox"}))
                           (dom/td nil
                                   (dom/a #js {:href (route-store (:db/id store) (str "/products/" (:db/id p)))}
                                          (dom/span nil (:store.item/name p))))
                           (dom/td nil
                                   (dom/a #js {:href (route-store (:db/id store) (str "/products/" (:db/id p)))}
                                          (dom/span nil (:store.item/price p))))
                           (dom/td nil
                                   (dom/a #js {:href (route-store (:db/id store) (str "/products/" (:db/id p)))}
                                          (dom/span nil (:store.item/price p))))))
                 (:store/items store))))))))

(defn store-products [component store {:keys [product-id action] :as rp}]
  (if (or (= action "create") (some? product-id))
    (let [product-id (when (some? product-id)
                       (c/parse-long product-id))
          selected-product (some #(when (= (:db/id %) product-id) %) (:store/items store))]
      (product-edit-form component selected-product))
    (products-list component store)))

(defui StoreDashboard
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/store [:store/uuid
                    :store/name
                    {:store/owners [{:store.owner/user [:user/email]}]}
                    :store/stripe
                    {:store/items [:store.item/name
                                   {:store.item/photos [:photo/path]}
                                   {:store.item/skus [:store.item.sku/quantity
                                                      :store.item.sku/value]}]}
                    :store/collections]}
     ;{:query/stripe [:stripe/account :stripe/products]}
     :query/route-params
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
       (let [{:keys [query/route-params]} (om/props this)
             {:keys [product-id]} route-params]
         (msg/om-transact! this `[(store/delete-product ~{:product {:db/id (c/parse-long product-id)}})]))))

  #?(:cljs
     (update-product [this]
                     (let [{:keys [query/route-params]} (om/props this)
                           {:keys [uploaded-photo]} (om/get-state this)
                           {:keys [input-price input-name input-sku-price input-sku-value input-sku-quantity]} form-elements
                           product {:name     (get-input-value input-name)
                                    :price    (get-input-value input-price)
                                    :currency "CAD"
                                    :photo    uploaded-photo}]

                       (cond (some? (:product-id route-params))
                             (msg/om-transact! this `[(store/update-product ~{:product    product
                                                                              :product-id (:product-id route-params)
                                                                              :store-id   (:store-id route-params)})
                                                  :query/store])

                             (= (:action route-params) "create")
                             (msg/om-transact! this `[(store/create-product
                                                        ~{:product  (assoc product :id (db/squuid)
                                                                                   :skus [{:id       (db/squuid)
                                                                                           :price    (get-input-value input-sku-price)
                                                                                           :value    (get-input-value input-sku-value)
                                                                                           :quantity (get-input-value input-sku-quantity)
                                                                                           :type     (if (get-input-value input-sku-value)
                                                                                                       :store.item.sku.type/finite
                                                                                                       :store.item.sku.type/infinite)}])
                                                          :store-id (:store-id route-params)})
                                                      :query/store]))
                       (om/update-state! this dissoc :uploaded-photo))))
  (componentDidUpdate [this _ _]
    (let [{:keys [query/store]} (om/props this)
          message-final-fn (fn [m] (when m (msg/final? m)))
          update-msg (msg/last-message this 'store/update-product)
          create-msg (msg/last-message this 'store/create-product)
          delete-msg (msg/last-message this 'store/delete-product)
          action-finished? (or (message-final-fn update-msg) (message-final-fn create-msg) (message-final-fn delete-msg))]
      #?(:cljs
         (when action-finished?
           (set! js/window.location.href (route-store (:db/id store) "/products"))))))

  (render [this]
    (let [{:keys [proxy/navbar query/store query/route-params]} (om/props this)
          {:keys [dashboard-option]} route-params]
      ;(debug "My Store: " store)
      ;(debug "Route params: " route-params)
      ;(debug "PRoducts: " stripe)
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
                (menu/item-tab {:active? (= dashboard-option "products")
                                :href    (route-store (:db/id store) "/products")} "Products")
                (menu/item-tab {:active? (= dashboard-option "orders")
                                :href    (route-store (:db/id store) "/orders")} "Orders"))))
          (cond (= dashboard-option "products")
                (store-products this store route-params)
                (= dashboard-option "orders")
                (store-orders this store route-params)))))))

(def ->StoreDashboard (om/factory StoreDashboard))
