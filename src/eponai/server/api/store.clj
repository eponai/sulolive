(ns eponai.server.api.store
  (:require
    [eponai.common.database :as db]
    [eponai.server.datomic.format :as f]
    [eponai.server.external.aws-s3 :as s3]
    [eponai.server.external.stripe :as stripe]
    [taoensso.timbre :refer [debug info]]
    [eponai.common.format :as cf]
    [eponai.server.external.cloudinary :as cloudinary]
    [eponai.common.format.date :as date]))


(defn create [{:keys [state auth system]} {:keys [country name]}]
  (let [stripe-account (stripe/create-account (:system/stripe system) {:country country})
        new-store {:db/id            (db/tempid :db.part/user)
                   :store/uuid       (db/squuid)
                   :store/profile    {:store.profile/name name}
                   :store/stripe     {:db/id         (db/tempid :db.part/user)
                                      :stripe/id     (:id stripe-account)
                                      :stripe/secret (:secret stripe-account)
                                      :stripe/publ   (:publ stripe-account)}
                   :store/owners     {:store.owner/role :store.owner.role/admin
                                      :store.owner/user (:user-id auth)}
                   :store/created-at (date/current-millis)}
        stream {:db/id        (db/tempid :db.part/user)
                :stream/store (:db/id new-store)
                :stream/state :stream.state/offline}]
    (db/transact state [new-store
                        stream])
    (db/pull (db/db state) [:db/id] [:store/uuid (:store/uuid new-store)])))

(defn retracts [old-entities new-entities]
  (let [removed (filter #(not (contains? (into #{} (map :db/id new-entities)) (:db/id %))) old-entities)]
    (reduce (fn [l remove-photo]
              (conj l [:db.fn/retractEntity (:db/id remove-photo)]))
            []
            removed)))

(defn edit-many-txs [entity attribute old new]
  (let [db-txs-retracts (retracts old new)]
    (reduce (fn [l e]
              (if (db/tempid? (:db/id e))
                (conj l e [:db/add entity attribute (:db/id e)])
                (conj l e)))
            db-txs-retracts
            new)))

(defn photo-entities [cloudinary ps]
  (map-indexed (fn [i new-photo]
                 (if (some? (:url new-photo))
                   (f/item-photo (cloudinary/upload-dynamic-photo cloudinary new-photo) i)
                   (-> new-photo (assoc :store.item.photo/index i) cf/add-tempid)))
               ps))

(defn create-product [{:keys [state system]} store-id {:store.item/keys [photos skus section] :as params}]
  (let [new-section (cf/add-tempid section)
        product (cond-> (f/product params)
                        (some? new-section)
                        (assoc :store.item/section new-section))

        ;; Craete product transactions
        product-txs (cond-> [product
                             [:db/add store-id :store/items (:db/id product)]]
                            (db/tempid? new-section)
                            (conj new-section [:db/add store-id :store/sections (:db/id new-section)]))

        ;; Create SKU transactions
        new-skus (map (fn [s] (f/sku s)) skus)
        product-sku-txs (into product-txs (edit-many-txs (:db/id product) :store.item/skus [] new-skus))

        ;; Create photo transactions, upload to S3 if necessary
        new-photos (photo-entities (:system/cloudinary system) photos)
        product-sku-photo-txs (into product-sku-txs (edit-many-txs (:db/id product) :store.item/photos [] new-photos))]

    ;; Transact all updates to Datomic once
    (db/transact state product-sku-photo-txs)))

(defn update-product [{:keys [state system]} product-id {:store.item/keys [photos skus section] :as params}]
  (let [old-item (db/pull (db/db state) [:db/id :store.item/photos :store.item/skus {:store/_items [:db/id]}] product-id)
        old-skus (:store.item/skus old-item)
        old-photos (:store.item/photos old-item)

        ;_ (debug "GETTING OLD ITEM: " old-item)
        store (:store/_items old-item)
        ;; Update product with new info, name/description, etc. Collections are updated below.
        new-section (cf/add-tempid section)
        new-product (cond-> (f/product (assoc params :db/id product-id))
                            (some? new-section)
                            (assoc :store.item/section new-section))
        product-txs (cond-> [new-product]
                            (db/tempid? (:db/id new-section))
                            (conj new-section [:db/add (:db/id store) :store/sections (:db/id new-section)]))

        ;; Update SKUs, remove SKUs not included in the skus collection from the client.
        new-skus (map (fn [s] (f/sku s)) skus)
        product-sku-txs (into product-txs (edit-many-txs product-id :store.item/skus old-skus new-skus))

        ;; Update photos, remove photos that are not included in the photos collections from the client.
        new-photos (photo-entities (:system/cloudinary system) photos)
        product-sku-photo-txs (into product-sku-txs (edit-many-txs product-id :store.item/photos old-photos new-photos))]

    ;; Transact updates into datomic
    (db/transact state product-sku-photo-txs)))

(defn update-sections [{:keys [state]} store-id {:keys [sections]}]
  (let [old-store (db/pull (db/db state) [:db/id :store/sections] store-id)
        old-sections (:store/sections old-store)
        new-sections (map cf/add-tempid sections)
        section-txs (into [] (edit-many-txs store-id :store/sections old-sections new-sections))]
    (when (not-empty section-txs)
      (db/transact state section-txs))))

(defn create-shipping-rule [{:keys [state]} store-id {:keys [shipping-rule]}]
  (let [{old-shipping :store/shipping} (db/pull (db/db state) [:db/id :store/shipping] store-id)
        new-rule (f/shipping-rule shipping-rule)]
    (debug "New rule: " new-rule)
    (if old-shipping
      (db/transact state [new-rule
                          [:db/add (:db/id old-shipping) :shipping/rules (:db/id new-rule)]])
      (let [new-shipping (cf/add-tempid {:shipping/rules [new-rule]})]
        (db/transact state [new-shipping
                            [:db/add store-id :store/shipping (:db/id new-shipping)]])))))

(defn update-shipping-rule [{:keys [state]} rule-id {:shipping.rule/keys [rates] :as params}]
  (let [old-rule (db/pull (db/db state) [:db/id :shipping.rule/rates] rule-id)
        old-rates (:shipping.rule/rates old-rule)
        new-rule (f/shipping-rule (assoc params :db/id rule-id))

        rule-txs [new-rule]

        new-rates (map #(f/shipping-rate %) rates)
        rule-rates-txs (into rule-txs (edit-many-txs rule-id :shipping.rule/rates old-rates new-rates))]
    (db/transact state rule-rates-txs)))

(defn delete-product [{:keys [state]} product-id]
  (db/transact state [[:db.fn/retractEntity product-id]]))

(defn ->order [state o store-id user-id]
  (let [store (db/lookup-entity (db/db state) store-id)]
    (assoc o :order/store store :order/user user-id)))

(defn create-order [{:keys [state system auth]} store-id {:keys [items shipping source subtotal shipping-rate]}]
  (let [
        {:keys [stripe/id]} (stripe/pull-stripe (db/db state) store-id)
        user-stripe (stripe/pull-user-stripe (db/db state) (:user-id auth))
        {:keys [user/cart]} (db/pull (db/db state) [{:user/cart [:db/id]}] (:user-id auth))
        {:keys [shipping/address]} shipping
        shipping-fee (:amount shipping-rate 0)

        total-amount (+ subtotal shipping-fee)
        application-fee (* 0.2 subtotal)                    ;Convert to cents for Stripe
        transaction-fee (* 0.029 total-amount)
        sulo-fee (+ application-fee transaction-fee)
        destination-amount (- total-amount sulo-fee)

        shipping-item (cf/remove-nil-keys {:order.item/type        :order.item.type/shipping
                                           :order.item/amount      (bigdec shipping-fee)
                                           :order.item/title       (:title shipping-rate)
                                           :order.item/description (:description shipping-rate)})
        sulo-fee-item (cf/remove-nil-keys {:order.item/type        :order.item.type/sulo-fee
                                           :order.item/amount      (bigdec sulo-fee)
                                           ;:order.item/title       "SULO Live fee"
                                           :order.item/description "Service fee"})
        order (-> (f/order {:order/items      items
                            :order/uuid       (db/squuid)
                            :order/shipping   shipping
                            :order/user       (:user-id auth)
                            :order/store      store-id
                            :order/amount     (bigdec total-amount)
                            :order/created-at (date/current-millis)})
                  (update :order/items conj shipping-item sulo-fee-item))]
    (when (some? user-stripe)
      (let [charge (stripe/create-charge (:system/stripe system) {:amount      (int (* 100 total-amount)) ;Convert to cents for Stripe
                                                                  ;:application_fee (int (+ application-fee transaction-fee))
                                                                  :currency    "cad"
                                                                  :customer    (:stripe/id user-stripe)
                                                                  :source      source
                                                                  :metadata    {:order_uuid (:order/uuid order)}
                                                                  :shipping    {:name    (:shipping/name shipping)
                                                                                :address {:line1       (:shipping.address/street address)
                                                                                          :line2       (:shipping.address/street2 address)
                                                                                          :postal_code (:shipping.address/postal address)
                                                                                          :city        (:shipping.address/locality address)
                                                                                          :state       (:shipping.address/region address)
                                                                                          :country     (:shipping.address/country address)}}
                                                                  :destination {:account id
                                                                                :amount  (int (* 100 destination-amount))}}) ;Convert to cents for Stripe


            charge-entity {:db/id     (db/tempid :db.part/user)
                           :charge/id (:charge/id charge)}
            is-paid? (:charge/paid? charge)
            order-status (if is-paid? :order.status/paid :order.status/created)
            charged-order (assoc order :order/status order-status :order/charge (:db/id charge-entity))
            retract-from-cart-txs (map (fn [sku]
                                         [:db/retract (:db/id cart) :user.cart/items (:db/id sku)])
                                       items)

            result-db (:db-after (db/transact state (into [charge-entity
                                                           charged-order]
                                                          retract-from-cart-txs)))]
        ;(when (some? auth)
        ;  (if (some? user-stripe)
        ;    (stripe/update-customer (:system/stripe system) (:stripe/id user-stripe) {:source source})
        ;    (let [user (db/pull (db/db state) [:user/email] (:user-id auth))
        ;          customer (stripe/create-customer (:system/stripe system) {:email    (:user/email user)
        ;                                                                    :metadata {:id (:user-id auth)}
        ;                                                                    :source   source})
        ;          new-stripe {:db/id     (db/tempid :db.part/user)
        ;                      :stripe/id (:stripe/id customer)}]
        ;      (db/transact state [new-stripe
        ;                          [:db/add (:user-id auth) :user/stripe (:db/id new-stripe)]]))))
        ; Return order entity to redirect in the client
        (db/pull result-db [:db/id] [:order/uuid (:order/uuid order)])))))

(defn update-order [{:keys [state system]} store-id order-id {:keys [order/status]}]
  (let [old-order (db/pull (db/db state) [:db/id :order/status {:order/charge [:charge/id]}] order-id)
        allowed-transitions {:order.status/created   #{:order.status/paid :order.status/canceled}
                             :order.status/paid      #{:order.status/fulfilled :order.status/canceled}
                             :order.status/fulfilled #{:order.status/returned}}
        old-status (:order/status old-order)
        is-status-transition-allowed? (contains? (get allowed-transitions old-status) status)]
    (if is-status-transition-allowed?
      (let [should-refund? (contains? #{:order.status/canceled :order.status/returned} status)]
        (when should-refund?
          (stripe/create-refund (:system/stripe system) {:charge (get-in old-order [:order/charge :charge/id])}))
        (db/transact state [[:db/add order-id :order/status status]]))
      (throw (ex-info (str "Order status transition not allowed, " status " can only transition to " (get allowed-transitions status))
                      {:order-status        status
                       :message             "Your order status could not be updated."
                       :allowed-transitions allowed-transitions})))))

(defn account [{:keys [state system]} store-id]
  (let [{:keys [stripe/id] :as s} (stripe/pull-stripe (db/db state) store-id)]
    (when (some? id)
      (assoc (stripe/get-account (:system/stripe system) id) :db/id (:db/id s)))))