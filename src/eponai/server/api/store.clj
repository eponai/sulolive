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
    (when (first skus)
      (let [db-sku (f/sku (first skus))]
        (db/transact state [db-sku
                            [:db/add [:store.item/uuid (:store.item/uuid db-product)] :store.item/skus (:db/id db-sku)]])))

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
  (let [old-item (db/pull (db/db state) [:db/id {:store.item/photos [:db/id :store.item.photo/index {:store.item.photo/photo [:db/id :photo/path]}]}] product-id)
        old-photos (group-by :store.item.photo/index (:store.item/photos old-item))]

    ;; Update product in Stripe
    (let [new-product (f/product (assoc params :db/id product-id))]
      (db/transact-one state new-product))

    ; Update SKUs in product
    (when-let [sku (first skus)]
      (let [db-sku (f/sku sku)
            old-sku (db/pull (db/db state) [:db/id :store.item.sku/quantity] [:store.item.sku/uuid (:id sku)])]
        (db/transact state (cond-> [db-sku
                                    [:db/add product-id :store.item/skus (:db/id db-sku)]]
                                   (and (some? (:store.item.sku/quantity old-sku))
                                        (nil? (:store.item.sku/quantity db-sku)))
                                   (conj [:db/retract (:db/id old-sku) :store.item.sku/quantity (:store.item.sku/quantity old-sku)])))))

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

(defn get-order [{:keys [db system]} store-id order-id]
  (let [{:keys [stripe/secret]} (stripe/pull-stripe db store-id)
        {:keys [order/id]} (db/pull db [:order/id] order-id)]
    (stripe/get-order (:system/stripe system) secret id)))

(defn list-orders [{:keys [db system]} store-id]
  (let [{:keys [stripe/secret]} (stripe/pull-stripe db store-id)
        orders (db/pull-all-with db [:db/id :order/id :order/store :order/user] {:where   '[[?e :order/store ?s]]
                                                        :symbols {'?s store-id}})
        stripe-orders (stripe/list-orders (:system/stripe system) secret {:ids (map :order/id orders)})]
    (map (fn [o]
           (let [db-order (some #(when (= (:order/id %) (:order/id o)) %) orders)]
             (merge o db-order)))
         stripe-orders)))

(defn create-order [{:keys [state system auth]} store-id {:keys [items source shipping]}]
  (let [{:keys [stripe/secret]} (stripe/pull-stripe (db/db state) store-id)
        {:address/keys [full-name locality region country street1 postal]} shipping
        order-params {:currency "CAD"
                      :email    (:email auth)
                      :items    (map (fn [i] {:type   "sku"
                                              :parent (str i)}) items)
                      :shipping {:name        full-name
                                 :address {:line1       street1
                                           :city        locality
                                           :country     country
                                           :postal_code postal}}}
        order (stripe/create-order (:system/stripe system) secret order-params)]
    (debug "Created order: " order)
    (db/transact-one state {:db/id       (db/tempid :db.part/user)
                            :order/id    (:order/id order)
                            :order/store store-id
                            :order/user  [:user/email (:email auth)]})
    (when source
      (stripe/pay-order (:system/stripe system) secret (:order/id order) source))
    (db/pull (db/db state) [:db/id] [:order/id (:order/id order)])
    ))

(defn update-order [{:keys [state system]} store-id order-id params]
  (let [{:keys [stripe/secret]} (stripe/pull-stripe (db/db state) store-id)]
    (stripe/update-order (:system/stripe system) secret order-id params)))

(defn account [{:keys [state system]} store-id]
  (let [{:keys [stripe/id] :as s} (stripe/pull-stripe (db/db state) store-id)]
    (when (some? id)
      (merge s
             (stripe/get-account (:system/stripe system) id)))))