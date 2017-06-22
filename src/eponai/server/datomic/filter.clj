(ns eponai.server.datomic.filter
  (:require
    [datomic.api :as datomic]
    [eponai.common.database :as db]
    [eponai.server.datomic.query :as query]
    [taoensso.timbre :as timbre :refer [debug trace]]
    ))

(defn- public-attr [_ _]
  (fn [_ _]
    true))

(defn- unauthorized-filter [_ _]
  false)

(defn- private-attr [_ _]
  unauthorized-filter)

(defn require-stores
  [store-ids f]
  (if (empty? store-ids)
    unauthorized-filter
    f))

(defn require-user [user-id f]
  (if (nil? user-id)
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

(defn- step-towards
  ([db from attr]
   (step-towards db from attr nil))
  ([db from {:keys [reverse? normalized-attr]} to]
   (let [index (if reverse? :vaet :eavt)]
     (if (nil? to)
       (db/datoms db index from normalized-attr)
       (db/datoms db index from normalized-attr to)))))

(defn- final-step [db from attr to]
  (if-not (set? to)
    (step-towards db from attr to)
    (->> (step-towards db from attr)
         (sequence (comp (map (if (:reverse? attr) :e :v))
                         (filter #(contains? to %))
                         ;; We only need one, so we can short circuit all sequences.
                         (take 1))))))

(defn walk-entity-path [db from [attr :as path] to]
  {:pre [(or (number? to) (set? to))]}
  (if (== 1 (count path))
    (final-step db from attr to)
    (sequence
      (comp (map (if (:reverse? attr) :e :v))
            (distinct)
            (mapcat #(walk-entity-path db % (rest path) to))
            (take 1))
      (step-towards db from attr))))

(defn desince [db]
  {:pre [(contains? db :sinceT)]}
  (cond-> db (some? (:sinceT db)) (assoc :sinceT nil)))

(defn attr-path [path]
  (into []
        (map #(hash-map :normalized-attr (query/normalize-attribute %)
                        :reverse? (query/reverse-lookup-attr? %)))
        path))

(defn user-path
  "Takes a user-id and a path to walk from the datom to reach the user-id."
  [path user-id]
  (let [attrs (attr-path path)]
    (require-user user-id
                  (fn [db [e]]
                    (some? (seq (walk-entity-path (desince db) e attrs user-id)))))))

(defn store-path [path store-ids]
  "Takes a store-ids and a path to walk from the datom to reach any of the stores."
  (let [attrs (attr-path path)
        ids (cond-> store-ids (= 1 (count store-ids)) (first))]
    (require-stores store-ids
                    (fn [db [e]]
                      (some? (seq (walk-entity-path (desince db) e attrs ids)))))))

(def filter-by-attribute
  (letfn [(user-attribute [user-id _]
            (require-user user-id (fn [_ [e]] (= e user-id))))
          (store-attribute [_ store-ids]
            (require-stores store-ids (fn [_ [e]] (contains? store-ids e))))

          (stream-owner [_ store-ids]
            (store-path [:stream/store] store-ids))
          (cart-owner [user-id _]
            (user-path [:user/_cart] user-id))
          (stripe-owner [user-id store-ids]
            (filter-or (user-path [:user/_stripe] user-id)
                       (store-path [:store/_stripe] store-ids)))
          (order-owner [user-id store-ids]
            (filter-or (user-path [:order/user] user-id)
                       (store-path [:order/store] store-ids)))
          (order-item-owner [user-id store-ids]
            (filter-or (user-path [:order/_items :order/user] user-id)
                       (store-path [:order/_items :order/store] store-ids)))
          (shipping-owner [user-id store-ids]
            (filter-or (store-path [:store/_shipping] store-ids)
                       (user-path [:order/_shipping :order/user] user-id)
                       (store-path [:order/_shipping :order/store] store-ids)))
          (shipping-address-owner [user-id store-ids]
            (filter-or (store-path [:shipping/_address :store/_shipping] store-ids)
                       (user-path [:shipping/_address :order/_shipping :order/user] user-id)
                       (store-path [:shipping/_address :order/_shipping :order/store] store-ids)))
          (order-charge-owner [user-id store-ids]
            (filter-or (user-path [:order/_charge :order/user] user-id)
                       (store-path [:order/_charge :order/store] store-ids)))]
    {
     ;; TODO getting exception if :db/ident is not specified here, bug?
     :db/ident                       public-attr

     :user/email                     user-attribute
     :user/verified                  user-attribute
     :user/stripe                    user-attribute
     :user/cart                      user-attribute
     :user/created-at                public-attr
     :user/profile                   public-attr
     :user.profile/name              public-attr
     :user.profile/photo             public-attr
     :sulo-locality/title            public-attr
     :sulo-locality/path             public-attr
     :sulo-locality/photo            public-attr
     :stripe/id                      stripe-owner
     :stripe/publ                    private-attr
     :stripe/secret                  private-attr
     :store/uuid                     public-attr
     :store/locality                 public-attr
     :store/created-at               public-attr
     :store/owners                   public-attr
     :store/stripe                   store-attribute
     :store/shipping                 public-attr
     :store/items                    public-attr
     :store/profile                  public-attr
     :store/sections                 public-attr
     :store.profile/name             public-attr
     :store.profile/description      public-attr
     :store.profile/return-policy    public-attr
     :store.profile/tagline          public-attr
     :store.profile/photo            public-attr
     :store.profile/cover            public-attr
     :store.profile/email            public-attr
     :store.section/label            public-attr
     :store.section/path             public-attr
     :store.owner/user               public-attr
     :store.owner/role               public-attr
     :store.item/uuid                public-attr
     :store.item/name                public-attr
     :store.item/description         public-attr
     :store.item/price               public-attr
     :store.item/created-at          public-attr
     :store.item/index               public-attr
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
     :chat.message/created-at        public-attr
     :order/charge                   order-owner
     :order/amount                   order-owner
     :order/status                   order-owner
     :order/store                    order-owner
     :order/user                     order-owner
     :order/uuid                     order-owner
     :order/shipping                 order-owner
     :order/created-at               order-owner
     :order/items                    order-owner
     :order.item/parent              order-item-owner
     :order.item/type                order-item-owner
     :order.item/amount              order-item-owner
     :order.item/description         order-item-owner
     :order.item/title               order-item-owner
     :order.item/photo               order-item-owner
     :charge/id                      order-charge-owner
     :shipping/rules                 public-attr
     :shipping.rule/destinations     public-attr
     :shipping.rule/title            public-attr
     :shipping.rule/rates            public-attr
     :shipping.rate/title            public-attr
     :shipping.rate/info             public-attr
     :shipping.rate/first            public-attr
     :shipping.rate/additional       public-attr
     :shipping.rate/free-above       public-attr
     :shipping/policy                public-attr
     :shipping/name                  shipping-owner
     :shipping/address               shipping-owner
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
     :photo/id                       public-attr
     :country/code                   public-attr
     :country/name                   public-attr
     :country/continent              public-attr
     :continent/code                 public-attr
     :continent/name                 public-attr}))

(defn filter-authed [authed-user-id authed-store-ids]
  (let [authed-store-ids (set authed-store-ids)
        filter-cache (atom {})
        get-filter (fn [db a]
                     (when-let [filter-fn
                                (get filter-by-attribute
                                     (:ident (datomic/attribute db a)))]
                       (if-some [filter (get @filter-cache filter-fn)]
                         filter
                         (let [filter (filter-fn authed-user-id authed-store-ids)]
                           (swap! filter-cache assoc filter-fn filter)
                           filter))))
        is-db-partition? (fn [db [e]]
                           (== (datomic/part e) (datomic/entid db :db.part/db)))]
    (fn [db datom]
      (or (is-db-partition? db datom)
          (if-let [filter (get-filter db (:a datom))]
            (filter db datom)
            (let [attr (datomic/attribute db (:a datom))]
              (debug (str "Database filter not implemented for attribute: " (:ident attr)))
              (throw (ex-info (str "Database filter not implemented for attribute: " (:ident attr))
                              {:datom     datom
                               :attribute attr}))))))))
