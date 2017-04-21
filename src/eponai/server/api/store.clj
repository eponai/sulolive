(ns eponai.server.api.store
  (:require
    [eponai.common.database :as db]
    [eponai.server.datomic.format :as f]
    [eponai.server.external.aws-s3 :as s3]
    [eponai.server.external.stripe :as stripe]
    [taoensso.timbre :refer [debug info]]))

(defn create-product [{:keys [state system]} store-id {:store.item/keys [uuid photos skus] :as params}]
  (let [db-product (f/product params)]
    ;; Transact new product into Datomic
    (db/transact state [db-product
                        [:db/add store-id :store/items (:db/id db-product)]])

    ;Create SKUs for product
    (when-let [product-skus (not-empty skus)]
      (let [sku-entities (map (fn [s] (f/sku s)) product-skus)
            db-txs (reduce (fn [l sku]
                             (conj l
                                   sku
                                   [:db/add [:store.item/uuid (:store.item/uuid db-product)] :store.item/skus (:db/id sku)]))
                           []
                           sku-entities)]
        (db/transact state db-txs)))

    ;; Upload product photos
    (when-let [new-photos (not-empty photos)]
      (let [uploads (map-indexed
                      (fn [i new-photo]
                        (let [photo-url (s3/upload-photo (:system/aws-s3 system) new-photo)
                              photo-upload (f/item-photo photo-url i)]
                          photo-upload))
                      new-photos)
            db-txs (reduce
                     (fn [l photo-upload]
                       (conj l photo-upload [:db/add [:store.item/uuid (:store.item/uuid db-product)] :store.item/photos (:db/id photo-upload)]))
                     []
                     uploads)]
        (db/transact state db-txs)))))

(defn update-product [{:keys [state system]} product-id {:store.item/keys [photos skus] :as params}]
  (let [old-item (db/pull (db/db state) [:db/id {:store.item/photos [:db/id :store.item.photo/index {:store.item.photo/photo [:db/id :photo/path]}]}
                                         :store.item/skus] product-id)
        old-skus (:store.item/skus old-item)
        old-photos (group-by :store.item.photo/index (:store.item/photos old-item))]

    ;; Update product in Stripe
    (let [new-product (f/product (assoc params :db/id product-id))]
      (db/transact-one state new-product))

    ; Update SKUs in product
    (let [db-skus (map (fn [s] (f/sku s)) skus)
          removed-skus (filter #(not (contains? (into #{} (map :db/id db-skus)) (:db/id %))) old-skus)

          db-txs-retracts (reduce (fn [l remove-sku]
                                    (conj l [:db.fn/retractEntity (:db/id remove-sku)]))
                                  []
                                  removed-skus)
          db-txs (reduce (fn [l sku]
                           (if (db/tempid? (:db/id sku))
                             (conj l sku [:db/add product-id :store.item/skus (:db/id sku)])
                             (conj l sku)))
                         db-txs-retracts
                         db-skus)]
      (db/transact state db-txs))

    ;; Upload photos
    (let [
          ; Create photo entities, upload new photos to S3 (they have a :location key sent from the client)
          ; If it's an existing photo, we just update the index in case that's changed
          uploads (map-indexed
                    (fn [i new-photo]
                      (if (some? (:location new-photo))
                        (let [photo-url (s3/upload-photo (:system/aws-s3 system) new-photo)]
                          (f/item-photo photo-url i))
                        (assoc new-photo :store.item.photo/index i)))
                    photos)
          ; Create Datomic transactions necessary to update the photos
          db-txs (reduce (fn [l photo-upload]
                           (let [index (:store.item.photo/index photo-upload)
                                 ; Get the old photo on the same index
                                 old-photo (first (get old-photos index))]
                             ; If we have an old photo on the same index we want to update that entity
                             (if (some? old-photo)
                               ; Check if this our photo upload is a new photo upload (created when uploaded to S3)
                               (cond (db/tempid? (:db/id photo-upload))
                                     ;; If it's new, remove the old photo on the same index and replace
                                     (conj l
                                           [:db.fn/retractEntity (:db/id old-photo)]
                                           photo-upload
                                           [:db/add product-id :store.item/photos (:db/id photo-upload)])
                                     ; If we have an existing entity for the old index, just update the new photo entity if it's not already the same.
                                     (not= (get-in old-photo [:store.item.photo/photo :db/id]) (get-in photo-upload [:store.item.photo/photo :db/id]))
                                     (conj l
                                           [:db.fn/retractEntity (get-in old-photo [:store.item.photo/photo :db/id])]
                                           [:db/add (:db/id photo-upload) :store.item.photo/photo (:store.item.photo/photo photo-upload)])
                                     :else
                                     l
                                     )
                               ;; If we didn't have an old photo item with the same index
                               (conj l
                                     photo-upload
                                     [:db/add product-id :store.item/photos (:db/id photo-upload)]))))
                         []
                         uploads)]

      (if (< (count uploads) (count old-photos))
        (let [db-txs-with-retracts (reduce (fn [l index]
                                             (let [old-photo (first (get old-photos index))]
                                               (conj l [:db.fn/retractEntity (:db/id old-photo)])))
                                           db-txs
                                           (range (count uploads) (count old-photos)))]
          (db/transact state db-txs-with-retracts))
        (db/transact state db-txs)))))

(defn delete-product [{:keys [state]} product-id]
  (db/transact state [[:db.fn/retractEntity product-id]]))

(defn ->order [state o store-id user-id]
  (let [store (db/lookup-entity (db/db state) store-id)]
    (assoc o :order/store store :order/user user-id)))

(defn create-order [{:keys [state system auth]} store-id {:keys [items source shipping]}]
  (let [{:keys [stripe/secret]} (stripe/pull-stripe (db/db state) store-id)
        order (f/order {:order/items    items
                        :order/uuid     (db/squuid)
                        :order/shipping shipping
                        :order/user     [:user/email (:email auth)]
                        :order/store    store-id})
        result-db (:db-after (db/transact-one state order))]
    (when source
      (let [charge (stripe/create-charge (:system/stripe system) secret {:amount          "1000"
                                                                         :application_fee "200"
                                                                         :currency        "cad"
                                                                         :source          source
                                                                         :metadata        {:order_uuid (:order/uuid order)}})
            charge-entity {:db/id     (db/tempid :db.part/user)
                           :charge/id (:charge/id charge)}]
        (db/transact state [charge-entity
                            [:db/add [:order/uuid (:order/uuid order)] :order/charge (:db/id charge-entity)]])))
    ;; Return order entity to redirect in the client
    (db/pull result-db [:db/id] [:order/uuid (:order/uuid order)])))

(defn update-order [{:keys [state system]} store-id order-id params]
  (let [{:keys [stripe/secret]} (stripe/pull-stripe (db/db state) store-id)]
    ))

(defn account [{:keys [state system]} store-id]
  (let [{:keys [stripe/id] :as s} (stripe/pull-stripe (db/db state) store-id)]
    (when (some? id)
      (merge s
             (stripe/get-account (:system/stripe system) id)))))