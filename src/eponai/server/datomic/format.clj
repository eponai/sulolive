(ns eponai.server.datomic.format
  (:require [clj-time.core :as t]
            [clj-time.coerce :as c]
            [datomic.api :only [db a] :as d]
            [eponai.common.format :as cf]
            [eponai.common.format.date :as date]
            [eponai.common.format :as common.format]
            [eponai.common.database :as db]
            [eponai.common.ui.om-quill :as quill]))


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

(defn item-photo [p index]
  {:db/id                  (d/tempid :db.part/user)
   :store.item.photo/photo (cf/add-tempid (select-keys p [:photo/path :photo/id]))
   :store.item.photo/index index})

(defn inventory [i]
  (-> (select-keys i [:db/id :store.item.sku.inventory/value :store.item.sku.inventory/type])
      common.format/add-tempid))

(defn sku [s]
  (let [sku (-> (select-keys s [:db/id :store.item.sku/variation :store.item.sku/inventory])
                common.format/add-tempid)]
    (cond-> sku
            (some? (:store.item.sku/inventory sku))
            (update :store.item.sku/inventory inventory))))

(defn input->price [price]
  (when price
    (bigdec price)))

(defn store-section [{:keys [value label]}]
  (if (number? value)
    {:db/id               value
     :store.section/label label}
    {:db/id               (db/tempid :db.part/user)
     :store.section/label label}))

(defn product [params]
  (-> (select-keys params [:db/id :store.item/name :store.item/description :store.item/price :store.item/uuid :store.item/category])
      (assoc :store.item/created-at (date/current-millis))
      ;(update :store.item/skus #(map sku %))
      (update :store.item/description #(when (some? %) (cf/str->bytes %)))
      (update :store.item/price input->price)
      common.format/add-tempid
      common.format/remove-nil-keys))

(defn auth0->user [auth0]
  {:db/id           (d/tempid :db.part/user)
   :user/email      (:email auth0)
   :user/verified   (:email_verified auth0)
   :user/created-at (date/current-millis)})

(defn shipping [s]
  (let [address* #(-> (select-keys % [:shipping.address/street
                                      :shipping.address/street2
                                      :shipping.address/locality
                                      :shipping.address/postal
                                      :shipping.address/region
                                      :shipping.address/country])
                      cf/remove-nil-keys)]
    (-> (select-keys s [:shipping/address :shipping/name :shipping/policy])
        (update :shipping/address address*)
        (update :shipping/policy #(cf/str->bytes (quill/sanitize-html %)))
        cf/add-tempid
        cf/remove-nil-keys)))

(defn shipping-rate [r]
  (-> r
      (select-keys [:shipping.rate/first :shipping.rate/additional :shipping.rate/free-above :shipping.rate/title :shipping.rate/info])
      (update :shipping.rate/first input->price)
      (update :shipping.rate/additional input->price)
      (update :shipping.rate/free-above input->price)
      cf/add-tempid
      cf/remove-nil-keys))

(defn shipping-rule [sr]
  (letfn [(destination* [d]
            (-> d
                (select-keys [:country/code :country/name])
                cf/add-tempid))]
    (-> (select-keys sr [:shipping.rule/destinations :shipping.rule/rates :shipping.rule/title])
        (update :shipping.rule/destinations #(map destination* %))
        (update :shipping.rule/rates #(map shipping-rate %))
        cf/add-tempid)))

(defn order [o]
  (let [item* (fn [sku]
                (let [product (:store.item/_skus sku)
                      photo (-> (sort-by :store.item.photo/index (:store.item/photos product))
                                first
                                :store.item.photo/photo)]
                  (cf/remove-nil-keys
                    {:db/id                  (db/tempid :db.part/user)
                     :order.item/type        :order.item.type/sku
                     :order.item/parent      (:db/id sku)
                     :order.item/description (:store.item.sku/variation sku)
                     :order.item/photo       photo
                     :order.item/title       (get-in sku [:store.item/_skus :store.item/name])
                     :order.item/amount      (bigdec (get-in sku [:store.item/_skus :store.item/price]))})))]
    (-> (select-keys o [:db/id :order/uuid :order/shipping :order/user :order/store :order/items :order/amount :order/created-at])
        (update :order/shipping shipping)
        (update :order/items #(map item* %))
        cf/add-tempid)))