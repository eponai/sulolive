(ns eponai.server.api.store
  (:require
    [eponai.common.database :as db]
    [eponai.server.datomic.format :as f]
    [eponai.server.external.aws-s3 :as s3]
    [eponai.server.external.stripe :as stripe]
    [taoensso.timbre :refer [debug info]]))

;(defn new-sku [{:keys [value type quantity]}]
;  (cond-> {:db/id                (db/tempid :db.part/user)
;           :store.item.sku/value value
;           :store.item.sku/type  type}
;          (some? quantity)
;          (assoc :store.item.sku/quantity quantity)))

(defn list-products [{:keys [db system]} store-id]
  (let [{:keys [stripe/secret]} (stripe/pull-stripe db store-id)
        {:keys [store/items]} (db/pull db [{:store/items [:store.item/uuid]}] store-id)]
    (stripe/list-products (:system/stripe system) secret {:ids (map :store.item/uuid items)})))

(defn create-product [{:keys [state system]} store-id {:keys [id photo skus] product-name :name :as params}]
  {:pre [(string? product-name) (uuid? id)]}
  (let [{:keys [stripe/secret]} (stripe/pull-stripe (db/db state) store-id)
        db-product (f/product params)]

    (stripe/create-product (:system/stripe system) secret params)
    ;; Transact new product into Datomic
    (db/transact state [db-product
                        [:db/add store-id :store/items (:db/id db-product)]])
    (debug "Created product: " db-product)

    ;; Create SKUs for product
    (when (first skus)
      (let [db-sku (f/sku (first skus))]
        (stripe/create-sku (:system/stripe system) secret id (first skus))
        (db/transact state [db-sku
                            [:db/add [:store.item/uuid (:store.item/uuid db-product)] :store.item/skus (:db/id db-sku)]])))

    ;; Upload product photos
    (when photo
      (let [photo-url (s3/upload-photo (:system/aws-s3 system) photo)
            photo-upload (f/photo photo-url)]
        (db/transact state [photo-upload
                            [:db/add [:store.item/uuid (:store.item/uuid db-product)] :store.item/photos (:db/id photo-upload)]])))))

(defn update-product [{:keys [state system]} store-id product-id {:keys [photo description skus price] :as params}]
  (let [{:keys [store.item/uuid store.item/photos]} (db/pull (db/db state) [:store.item/uuid :store.item/photos] product-id)
        {:keys [stripe/secret]} (stripe/pull-stripe (db/db state) store-id)
        old-photo (first photos)]

    ;; Update product in Stripe
    (stripe/update-product (:system/stripe system) secret (str uuid) params)
    (let [new-product (cond-> {:store.item/uuid uuid
                               :store.item/name (:name params)}
                              (some? price)
                              (assoc :store.item/price (f/input->price price))
                              (some? description)
                              (assoc :store.item/description (.getBytes description)))]
      (db/transact-one state new-product))

    ;; Update SKUs in product
    (when-let [sku (first skus)]
      (stripe/update-sku (:system/stripe system) secret (str (:id sku)) sku)
      (let [db-sku (f/sku sku)
            old-sku (db/pull (db/db state) [:db/id :store.item.sku/quantity] [:store.item.sku/uuid (:id sku)])]
        (db/transact state (cond-> [db-sku
                                    [:db/add product-id :store.item/skus (:db/id db-sku)]]
                                   (and (some? (:store.item.sku/quantity old-sku))
                                        (nil? (:store.item.sku/quantity db-sku)))
                                   (conj [:db/retract (:db/id old-sku) :store.item.sku/quantity (:store.item.sku/quantity old-sku)])))))

    ;; Upload photo
    (when photo
      (debug "Upload photo: " photo)
      (let [photo-url (s3/upload-photo (:system/aws-s3 system) photo)
            photo-upload (f/photo photo-url)]
        (if (some? old-photo)
          (db/transact-one state (assoc photo-upload :db/id (:db/id old-photo)))
          (db/transact state [photo-upload
                              [:db/add [:store.item/uuid uuid] :store.item/photos (:db/id photo-upload)]]))))))

(defn delete-product [{:keys [state system]} product-id]
  (let [{:keys [store.item/uuid store.item/skus] :as item} (db/pull (db/db state) [:store.item/uuid {:store.item/skus [:db/id :store.item.sku/uuid]}] product-id)
        something (into {} (db/entity (db/db state) product-id))
        {:keys [stripe/secret]} (db/pull-one-with (db/db state) [:stripe/secret] {:where   '[[?s :store/items ?p]
                                                                                             [?s :store/stripe ?e]]
                                                                                  :symbols {'?p product-id}})
        deleted-skus (mapv (fn [sku]
                             (stripe/delete-sku (:system/stripe system) secret (str (:store.item.sku/uuid sku))))
                           skus)
        stripe-p (stripe/delete-product (:system/stripe system)
                                        secret
                                        (str uuid))]
    (when (not-empty skus)
      (debug "Deleted skus: " deleted-skus))
    (debug "Deleted product in stripe: " stripe-p)
    (db/transact state [[:db.fn/retractEntity product-id]])))

(defn get-order [{:keys [db system]} store-id order-id]
  (let [{:keys [stripe/secret]} (stripe/pull-stripe db store-id)]
    (stripe/get-order (:system/stripe system) secret order-id)))

(defn list-orders [{:keys [db system]} store-id]
  (let [{:keys [stripe/secret]} (stripe/pull-stripe db store-id)]
    (stripe/list-orders (:system/stripe system) secret)))

(defn create-order [{:keys [state system auth]} store-id {:keys [items source]}]
  (let [{:keys [stripe/secret]} (stripe/pull-stripe (db/db state) store-id)
        order-params {:currency "CAD"
                      :email    (:email auth)
                      :items    (map (fn [i] {:type   "sku"
                                              :parent (str i)}) items)}
        order (stripe/create-order (:system/stripe system) secret order-params)]
    (when source
      (stripe/pay-order (:system/stripe system) secret (:order/id order) source))))

(defn update-order [{:keys [state system]} store-id order-id params]
  (let [{:keys [stripe/secret]} (stripe/pull-stripe (db/db state) store-id)]
    (stripe/update-order (:system/stripe system) secret order-id params)))