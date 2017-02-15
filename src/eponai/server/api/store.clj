(ns eponai.server.api.store
  (:require
    [eponai.common.database :as db]
    [eponai.server.datomic.format :as f]
    [eponai.server.external.aws-s3 :as s3]
    [eponai.server.external.stripe :as stripe]
    [taoensso.timbre :refer [debug info]]))

(defn new-sku [{:keys [value type quantity]}]
  (cond-> {:db/id                (db/tempid :db.part/user)
           :store.item.sku/value value
           :store.item.sku/type  type}
          (some? quantity)
          (assoc :store.item.sku/quantity quantity)))

(defn create-product [{:keys [state system]} store-id {:keys [id photo skus] product-name :name :as params}]
  {:pre [(string? product-name) (uuid? id)]}
  (let [{:keys [stripe/secret]} (stripe/pull-stripe (db/db state) store-id)
        stripe-p (stripe/create-product (:system/stripe system) secret params)
        _ (debug "Create product with params: " params)
        stripe-sku (when (first skus)
                     (f/sku (stripe/create-sku (:system/stripe system) secret (:id stripe-p) (first skus))))
        photo-upload (when photo (s3/upload-photo (:system/aws-s3 system) photo))

        db-product (f/product params)
        txs (cond-> [db-product
                     [:db/add store-id :store/items (:db/id db-product)]]
                    (some? photo-upload)
                    (conj photo-upload
                          [:db/add (:db/id db-product) :store.item/photos (:db/id photo-upload)])

                    (some? stripe-sku)
                    (conj stripe-sku
                          [:db/add (:db/id db-product) :store.item/skus (:db/id stripe-sku)]))]
    (debug "Created product in stripe: " stripe-p)
    ;(debug "Uploaded item photo: " photo-upload)
    (info "Transacting new product: " txs)
    (db/transact state txs)))

(defn update-product [{:keys [state system]} store-id product-id {:keys [photo description] :as params}]
  (let [{:keys [store.item/uuid store.item/photos]} (db/pull (db/db state) [:store.item/uuid :store.item/photos] product-id)
        {:keys [stripe/secret]} (stripe/pull-stripe (db/db state) store-id)
        old-photo (first photos)

        ;; Update product in Stripe
        stripe-p (stripe/update-product (:system/stripe system)
                                        secret
                                        (str uuid)
                                        params)
        new-item (cond-> {:store.item/uuid uuid
                          :store.item/name (:name params)}
                         (some? description)
                         (assoc :store.item/description (.getBytes description)))

        ;; Upload photo
        photo-upload (when photo (s3/upload-photo (:system/aws-s3 system) photo))
        txs (if (some? photo-upload)
              (if (some? old-photo)
                [new-item
                 (assoc photo-upload :db/id (:db/id old-photo))]
                [new-item
                 photo-upload
                 [:db/add [:store.item/uuid uuid] :store.item/photos (:db/id photo-upload)]])
              [new-item])]
    (debug "Updated product in stripe: " stripe-p)
    (debug "Transaction into datomic: " txs)
    (db/transact state txs)))

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
  (let [{:keys [stripe/secret]} (stripe/pull-stripe db store-id)
        order (stripe/get-order (:system/stripe system) secret order-id)]
    (debug "Got orders: " (into [] order))
    order))

(defn list-orders [{:keys [db system]} store-id]
  (let [{:keys [stripe/secret]} (stripe/pull-stripe db store-id)
        orders (stripe/list-orders (:system/stripe system) secret)]
    (debug "Got orders: " (into [] orders))
    orders))

(defn create-order [{:keys [state system]} store-id order]
  (let [{:keys [stripe/secret]} (stripe/pull-stripe (db/db state) store-id)
        new-order (stripe/create-order (:system/stripe system) secret order)]
    (debug "Created new order in STRIPE: " new-order)
    new-order))