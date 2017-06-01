(ns eponai.server.datomic.filter
  "Filter maps can be combined, incrementally updated and
  applied as either AND or OR filters.

  A filter map is defined as:
  {:f filter-function
   :props map-of-values-which-describe-a-filter}.

  The filter function takes keys from :props as input
  and returns a datomic filter function (fn [db datom]).

  Each key in props contain:
  {:init initial-value
  :update-fn (fn [db new-eids old-val] return new-val)}
  The purpose of the :props is to describe a filter to be incrementaly
  updated. The param new-eids is either nil or a seq of entity ids.

  It sounds more complicated than it is..?
  Hopefully overtime, we figure out an easier way to describe
  incrementally updatable database filters."
  (:require [datomic.api :as d]
            [taoensso.timbre :as timbre :refer [debug trace]]
            ))

(defn- public-attr [_ _]
  (fn [_ _]
    true))

(defn- unauthorized-filter [_ _]
  false)

(defn require-stores
  [store-ids f]
  (if (empty? store-ids)
    unauthorized-filter
    f))

(defn require-user [user-id f]
  (if (some? user-id)
    unauthorized-filter
    f))

(defn filter-or
  "Combines filter functions with or. Removing any unauthorized filters, as they are not needed."
  [& fns]
  (if-some [fns (seq (remove #(identical? % unauthorized-filter) fns))]
    (reduce (fn [a b]
              (fn [db datom]
                (or (a db datom)
                    (b db datom))))
            (first fns)
            (rest fns))
    unauthorized-filter))

(defn- walk-entity-path [db from path to]
  ;; TODO: IMPLEMENT.
  ;; where to can be a number or a set of matching db/ids.
  ;; - Walk using d/datoms from <from> via <path> to <to>
  ;;   - Enter <to> as the value when we're at the end of the path.
  ;; - Remove "since" if it's on the database
  ;;   - Otherwise it's possible that we can't detect the path
  ;;   - (assoc (d/since (d/db conn) 1329) :sinceT nil)
  (throw (ex-info "TODO: Implement." {:from from :path path :to to}))
  )

(defn user-path
  "Takes a user-id and a path to walk from the datom to reach the user-id."
  [user-id path]
  (require-user user-id
                (fn [db [e]]
                  (walk-entity-path db e path user-id))))

(defn store-path [store-ids path]
  "Takes a store-ids and a path to walk from the datom to reach any of the stores."
  (require-stores store-ids
                  (fn [db [e]]
                    (walk-entity-path db e path store-ids))))

(def filter-by-attribute
  (letfn [(user-attribute [user-id _]
            (require-user user-id (fn [_ [e]] (= e user-id))))
          (store-attribute [_ store-ids]
            (require-stores store-ids (fn [_ [e]] (contains? store-ids e))))

          (stream-owner [_ store-ids]
            (store-path store-ids [:stream/store]))
          (cart-owner [user-id _]
            (user-path user-id [:user/_cart]))
          (stripe-owner [user-id store-ids]
            (filter-or (user-path user-id [:user/_stripe])
                       (store-path store-ids [:store/_stripe])))
          (order-owner [user-id store-ids]
            (filter-or (user-path user-id [:order/user])
                       (store-path store-ids [:order/store])))
          (order-item-owner [user-id store-ids]
            (filter-or (user-path user-id [:order/_items :order/user])
                       (store-path store-ids [:order/_items :order/store])))
          (shipping-address-owner [user-id store-ids]
            (filter-or (user-path user-id [:order/_shipping :order/user])
                       (store-path store-ids [:order/_shipping :order/store])))
          (order-charge-owner [user-id store-ids]
            (filter-or (user-path user-id [:order/_charge :order/user])
                       (store-path store-ids [:order/_charge :order/store])))]
    {
     :user/email                     public-attr
     :user/verified                  user-attribute
     :user/stripe                    user-attribute
     :user/cart                      user-attribute
     :user/profile                   public-attr
     :user.profile/name              public-attr
     :user.profile/photo             public-attr
     :stripe/id                      stripe-owner
     :stripe/publ                    stripe-owner
     :stripe/secret                  stripe-owner
     :store/uuid                     public-attr
     :store/stripe                   store-attribute
     :store/owners                   public-attr
     :store/items                    public-attr
     :store/profile                  public-attr
     :store/sections                 public-attr
     :store.profile/name             public-attr
     :store.profile/description      public-attr
     :store.profile/return-policy    public-attr
     :store.profile/tagline          public-attr
     :store.profile/photo            public-attr
     :store.profile/cover            public-attr
     :store.section/label            public-attr
     :store.section/path             public-attr
     :store.owner/user               public-attr
     :store.owner/role               public-attr
     :store.item/uuid                public-attr
     :store.item/name                public-attr
     :store.item/description         public-attr
     :store.item/price               public-attr
     :store.item/category            public-attr
     :store.item/section             public-attr
     :store.item/photos              public-attr
     :store.item/skus                public-attr
     :store.item.photo/photo         public-attr
     :store.item.photo/index         public-attr
     :store.item.sku/variation       public-attr
     :store.item.sku/inventory       public-attr
     :store.item.sku.inventory/type  public-attr
     :store.item.sku.inventory/value public-attr
     :photo/path                     public-attr
     :stream/title                   public-attr
     :stream/store                   public-attr
     :stream/state                   public-attr
     :stream/token                   stream-owner
     :user.cart/items                cart-owner
     :chat/store                     public-attr
     :chat/messages                  public-attr
     :chat.message/text              public-attr
     :chat.message/user              public-attr
     :order/charge                   order-owner
     :order/amount                   order-owner
     :order/status                   order-owner
     :order/store                    order-owner
     :order/user                     order-owner
     :order/uuid                     order-owner
     :order/shipping                 order-owner
     :order/items                    order-owner
     :order.item/parent              order-item-owner
     :order.item/type                order-item-owner
     :order.item/amount              order-item-owner
     :charge/id                      order-charge-owner
     :shipping/name                  shipping-address-owner
     :shipping/address               shipping-address-owner
     :shipping.address/street        shipping-address-owner
     :shipping.address/street2       shipping-address-owner
     :shipping.address/postal        shipping-address-owner
     :shipping.address/locality      shipping-address-owner
     :shipping.address/region        shipping-address-owner
     :shipping.address/country       shipping-address-owner
     :category/path                  public-attr
     :category/name                  public-attr
     :category/label                 public-attr
     :category/children              public-attr
     :user/created-at                public-attr
     :store/created-at               public-attr
     :store.item/created-at          public-attr
     :store.item/index               public-attr
     :photo/id                       public-attr}))

(defn filter-authed [authed-user-id authed-store-ids]
  (let [authed-store-ids (set authed-store-ids)
        filter-cache (atom {})
        get-filter (fn [db a]
                     (when-let [filter-fn (get filter-by-attribute (:ident (d/attribute db a)))]
                       (if-some [filter (get @filter-cache filter-fn)]
                         filter
                         (let [filter (filter-fn authed-user-id authed-store-ids)]
                           (swap! filter-cache assoc filter-fn filter)
                           filter))))
        db-partition-cache (atom nil)
        is-db-partition? (fn [db [e]]
                           (== (d/part e)
                               (if-let [db-part @db-partition-cache]
                                 db-part
                                 (reset! db-partition-cache (:db/id (d/entity db [:db/ident :db.part/db]))))))]
    (fn [db datom]
      ;; TODO: Remove this d/is-history?
      (or (d/is-history db)
          (is-db-partition? db datom)
          (if-let [filter (get-filter db (:a datom))]
            (filter db datom)
            (throw (ex-info "Filter not implemented for attribute"
                            {:datom     datom
                             :attribute (d/attribute db (:a datom))})))))))
