(ns eponai.server.datomic.format
  (:require [clj-time.core :as t]
            [clj-time.coerce :as c]
            [datomic.api :only [db a] :as d]
            [eponai.common.format :as cf]
            [eponai.common.format.date :as date]
            [eponai.common.format :as common.format]
            [eponai.common.database :as db]))


;; ------------------------ Create new entities -----------------

(defn fb-user
  "Create fb-user entity.

  Provide opts including keys that should be specifically set. Will consider keys:
  * :fb-user/id - required, user_id on Facebook.
  * :fb-user/token - required, access_token from Facebook

  Returns a map representing a fb-user entity, or nil if required keys are missing."
  [user opts]
  (let [id (:fb-user/id opts)
        token (:fb-user/token opts)]
    (when (and id
               token)
      {:db/id         (d/tempid :db.part/user)
       :fb-user/id    id
       :fb-user/token token
       :fb-user/user  (:db/id user)})))

(defn verification
  "Create verification db entity belonging to the provided entity (user for email verification).

  Provide opts including keys that should be specifically set. Will consider keys:
  * :verification/status - status of verification, default is :verification.status/pending.
  * :verification/created-at - timestamp if when verification was created, default value is now.
  * :verification/attribute - key in this entity with a value that this verification should verify, default is :user/email.
  * :verification/expires-at - timestamp for when verification expires.

  Returns a map representing a verification entity"
  [entity & [opts]]
  (let [attribute (or (:verification/attribute opts) :user/email)]
    (cond->
      {:db/id                   (d/tempid :db.part/user)
       :verification/status     (or (:verification/status opts) :verification.status/pending)
       :verification/created-at (or (:verification/created-at opts) (c/to-long (t/now)))
       :verification/uuid       (d/squuid)
       :verification/entity     (:db/id entity)
       :verification/attribute  attribute
       :verification/value      (get entity attribute)})))

(defn email-verification [entity & [opts]]
  (let [v (verification entity opts)
        expiry-time (c/to-long (t/plus (c/from-long (:verification/created-at v)) (t/minutes 15)))]
    (assoc v :verification/expires-at expiry-time)))

(defn project [user & [opts]]
  (cf/project (:db/id user) opts))

(defn stripe-account
  "Takes a map with stripe information and formats to be transacted into datomic.
  Adds tempids and associates the specified user."
  [user-id stripe-input]
  (let [customer (-> stripe-input
                     common.format/add-tempid
                     (assoc :stripe/user user-id))]
    (if (:stripe/subscription stripe-input)
      (update customer :stripe/subscription common.format/add-tempid)
      customer)))

(defn user [& args]
  (throw (ex-info "TODO, implement this"
                  {:cause "datomic.format/user does not exist anymore."
                   :args  args})))

(defn user-account-map [& args]
  (throw (ex-info "TODO, implement this"
                  {:cause "datomic.format/user-account-map does not exist anymore."
                   :args  args})))

(defn photo [url]
  {:pre [(string? url)]}
  {:db/id      (d/tempid :db.part/user)
   :photo/path url})

(defn item-photo [url index]
  {:db/id                  (d/tempid :db.part/user)
   :store.item.photo/photo (photo url)
   :store.item.photo/index index})

(defn inventory [i]
  (-> (select-keys i [:db/id :store.item.sku.inventory/value :store.item.sku.inventory/type])
      common.format/add-tempid))

(defn sku [s]
  (let [sku (-> (select-keys s [:db/id :store.item.sku/uuid :store.item.sku/variation :store.item.sku/inventory])
                common.format/add-tempid)]
    (cond-> sku
            (some? (:store.item.sku/inventory sku))
            (update :store.item.sku/inventory inventory))))

(defn input->price [price]
  (when price
    (bigdec price)))

(defn product [params]
  (-> (select-keys params [:db/id :store.item/name :store.item/description :store.item/price :store.item/uuid])
      ;(update :store.item/skus #(map sku %))
      (update :store.item/description #(when (some? %) (.getBytes %)))
      (update :store.item/price input->price)
      common.format/add-tempid
      common.format/remove-nil-keys))

(defn auth0->user [auth0]
  {:db/id         (d/tempid :db.part/user)
   :user/email    (:email auth0)
   :user/verified (:email_verified auth0)})

(defn shipping [s]
  (let [address* #(-> (select-keys % [:shipping.address/street
                                      :shipping.address/street2
                                      :shipping.address/locality
                                      :shipping.address/postal
                                      :shipping.address/region
                                      :shipping.address/country])
                      cf/remove-nil-keys)]
    (-> (select-keys s [:shipping/address :shipping/name])
        (update :shipping/address address*)
        cf/add-tempid)))

(defn order [o]
  (let [item* (fn [sku]
                {:db/id (db/tempid :db.part/user)
                 :order.item/type :order.item.type/sku
                 :order.item/parent (:db/id sku)})]
    (-> (select-keys o [:db/id :order/uuid :order/shipping :order/user :order/store :order/items])
        (update :order/shipping shipping)
        (update :order/items #(map item* %))
        cf/add-tempid)))