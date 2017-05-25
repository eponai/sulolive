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
  (:require [clojure.set :as s]
            [datomic.api :as d]
            [eponai.common.database :as db]
            [taoensso.timbre :as timbre :refer [debug trace]]))

;; Updating and applying filters

(defn update-props
  "Preforms an incremental update on the props of a filter-map.

  By adding the last time we update the props onto the map, we
  can quickly avoid computing new filters for the database."
  [db {:keys [::basis-t] :as props}]
  {:pre [(map? props)]}
  (let [basis-t (or basis-t -1)
        new-basis-t (d/basis-t db)]
    (if (= basis-t new-basis-t)
      (do
        (trace "avoiding update to filter:" basis-t)
        props)
      (let [db-since (d/since db basis-t)]
        (trace "Will update filter: " props)
        (let [updated-props (reduce-kv (fn [props key {:keys [init update-fn value]}]
                                         (assoc-in props [key :value]
                                                   (update-fn db db-since (or value init))))
                                       props
                                       (dissoc props ::basis-t))]
          (trace "Did update filter: " updated-props)
          (assoc updated-props ::basis-t new-basis-t))))))

(defn update-filters
  "Updating filters with db."
  [db filters]
  {:pre [(sequential? filters)]}
  (mapv #(assoc % :props (update-props db (:props %))) filters))

(defn- extract-prop-values
  "Takes a filter-map's props and assoc's the value of each key onto the keys."
  [props]
  {:pre [(map? props)]}
  (reduce-kv (fn [p k v] (assoc p k (:value v)))
             {}
             props))

(defn apply-filters
  "Applies filters with AND to a db."
  [db filters]
  {:pre [(sequential? filters)]}
  (reduce (fn [db {:keys [f props]}]
            (d/filter db (f (extract-prop-values props))))
          db
          filters))

(defn or-filter
  "Combines a filter-map such that the filter passes if either of the filters pass."
  [& filter-maps]
  ;; No filter has the same props keys.
  {:pre [(empty? (apply s/intersection (->> filter-maps (map :props) (map keys) (map set))))]}
  (let []
    (reduce (fn [filter1 filter2]
              {:props (merge (:props filter1) (:props filter2))
               :f     (fn [props]
                        (let [f1 ((:f filter1) props)
                              f2 ((:f filter2) props)]
                          (fn [db datom]
                            (or (f1 db datom)
                                (f2 db datom)))))})
            (first filter-maps)
            (rest filter-maps))))

(def user-owned-rule
  '[[(owner? ?user-id ?e)
     [?e :user/uuid ?user-id]]
    [(owner? ?user-id ?e)
     [?e ?ref-attr ?ref]
     (owner? ?user-id ?ref)]])

(defn user-entities [db db-since user-id]
  (comment
    "Not used yet, because we don't have a user/uuid thing"
    (let [query {:where   '[[$ ?e]
                            [$since ?e]
                            (owner? ?user-id ?e)]
                 :symbols {'$since   db-since
                           '?user-id user-id
                           '%        user-owned-rule}}]
      (db/all-with db query)))
  #{})

(defn user-attributes [db db-since]
  ;;TODO: Add more user entities? We want to filter out anything that has to do with sensitive
  ;;      user data. Such as what they have bought.
  (let [user-entity-keys []                                 ;; [:project/uuid :transaction/uuid]
        query {:find '[[?a ...]]
               :where        '[[$ ?e ?user-entity-key]
                               [$ ?e ?a]
                               [$since ?e]]
               :symbols      {'$since                 db-since
                              '[?user-entity-key ...] user-entity-keys}}]
    (db/find-with db query)))

(defn- user-specific-entities-filter [user-id]
  {:props {:user-entities {:init      #{}
                            :update-fn (fn [db db-since old-val]
                                         (into old-val (user-entities db db-since user-id)))}}
   :f     (fn [{:keys [user-entities]}]
            {:pre [(set? user-entities)]}
            ;; Returning a datomic filter function (fn [db datom])
            (fn [db [eid]]
              (contains? user-entities eid)))})

(defn- non-user-entities-filter-map []
  {:props {:user-attrs {:init      #{}
                        :update-fn (fn [db db-since old-val]
                                     (into old-val (user-attributes db db-since)))}}
   :f     (fn [{:keys [user-attrs]}]
            {:pre [(set? user-attrs)]}
            ;; Returning a datomic filter function (fn [db datom])
            (fn [db [eid attr]]
              (not (contains? user-attrs attr))))})

(defn authenticated-db-filters
  "When authenticated, we can access entities specific to one user
  or entities which do not contain user data (e.g. dates)."
  [user-id]
  (comment
    "TODO: Implement authed filters"
    [(or-filter (user-specific-entities-filter user-id)
                (non-user-entities-filter-map))])
  [])

(defn not-authenticated-db-filters []
  (comment
    "TODO: Implement public filters"
    [(non-user-entities-filter-map)])
  [])

(def public (constantly true))

(defn user-attribute [user-id]
  (fn [_ [e]]
    (= e user-id)))


(defn store-attribute [store-id]
  (fn [_ [e]]
    (= e store-id)))

(defn query [q]
  (fn [db [e a v]]
    (db/one-with db (db/merge-query q {:symbols {'?e e '?v v}}))))

(defn filter-or [& fns]
  (reduce (fn [a b]
            (fn [db datom]
              (or (a db datom)
                  (b db datom))))
          (first fns)
          (rest fns)))

(defn stripe-owner [user-id store-id]
  (query {:where   '[(or-join [?e]
                              [?user :user/stripe ?e]
                              [?store :store/stripe ?e])]
          :symbols {'?user  user-id
                    '?store store-id}}))

(defn order-owner [user-id store-id]
  (query {:where   '[(or-join [?e]
                              [?e :order/user ?user]
                              [?e :order/store ?store])]
          :symbols {'?user  user-id
                    '?store store-id}}))

(defn order-item-owner [user-id store-id]
  (fn [db [e]]
    ((order-owner user-id store-id)
      db
      [(:db/id (:order/_items (d/entity db e)))])))

(defn shipping-address-owner [user-id store-id]
  (fn [db [e]]
    ((order-owner user-id store-id)
      db
      [(:db/id (:order/_shipping (d/entity db e)))])))

(defn filter-by-attribute [{:keys [user-id store-id]}]
  {
   ;; public?
   :user/email                     public
   ;; user
   :user/verified                  (user-attribute user-id)
   ;; user
   :user/stripe                    (user-attribute user-id)
   ;; user
   :user/cart                      (user-attribute user-id)
   ;; public
   :user/profile                   public
   ;; public
   :user.profile/name              public
   ;; public
   :user.profile/photo             public
   ;; user or store-owner
   :stripe/id                      (stripe-owner user-id store-id)
   ;; store-owner
   :stripe/publ                    (stripe-owner user-id store-id)
   ;; store-owner
   :stripe/secret                  (stripe-owner user-id store-id)
   ;; public
   :store/uuid                     public
   ;; store-owner
   :store/stripe                   (store-attribute store-id)
   :store/owners                   public
   :store/items                    public
   :store/profile                  public
   :store/sections                 public
   :store.profile/name             public
   :store.profile/description      public
   :store.profile/return-policy    public
   :store.profile/tagline          public
   :store.profile/photo            public
   :store.profile/cover            public
   :store.section/label            public
   :store.section/path             public
   :store.owner/user               public
   :store.owner/role               public
   :store.item/uuid                public
   :store.item/name                public
   :store.item/description         public
   :store.item/price               public
   :store.item/category            public
   :store.item/section             public
   :store.item/photos              public
   :store.item/skus                public
   :store.item.photo/photo         public
   :store.item.photo/index         public
   :store.item.sku/variation       public
   :store.item.sku/inventory       public
   :store.item.sku.inventory/type  public
   :store.item.sku.inventory/value public
   :photo/path                     public
   :stream/title                   public
   :stream/store                   public
   :stream/state                   public
   ;; store-owner
   :stream/token                   (query {:where   '[[?e :stream/store ?store]]
                                           :symbols {'?store store-id}})
   :user.cart/items                (query {:where   '[[?user :user/cart ?e]]
                                           :symbols {'?user user-id}})
   :chat/store                     public
   :chat/messages                  public
   :chat.message/text              public
   :chat.message/user              public
   ;; store or user for the order. order-owner ?
   :order/charge                   (order-owner user-id store-id)
   :order/amount                   (order-owner user-id store-id)
   :order/status                   (order-owner user-id store-id)
   :order/store                    (order-owner user-id store-id)
   :order/user                     (order-owner user-id store-id)
   :order/uuid                     (order-owner user-id store-id)
   :order/shipping                 (order-owner user-id store-id)
   :order/items                    (order-owner user-id store-id)
   :order.item/parent              (order-item-owner user-id store-id)
   :order.item/type                (order-item-owner user-id store-id)
   :order.item/amount              (order-item-owner user-id store-id)
   ;; store or user?
   :charge/id                      (fn [db [e]]
                                     ((order-owner user-id store-id)
                                       db
                                       [(:db/id (:order/_charge (d/entity db e)))]))
   ;; order owner
   :shipping/name                  (shipping-address-owner user-id store-id)
   :shipping/address               (shipping-address-owner user-id store-id)
   :shipping.address/street        (shipping-address-owner user-id store-id)
   :shipping.address/street2       (shipping-address-owner user-id store-id)
   :shipping.address/postal        (shipping-address-owner user-id store-id)
   :shipping.address/locality      (shipping-address-owner user-id store-id)
   :shipping.address/region        (shipping-address-owner user-id store-id)
   :shipping.address/country       (shipping-address-owner user-id store-id)
   :category/path                  public
   :category/name                  public
   :category/label                 public
   :category/children              public
   :user/created-at                public
   :store/created-at               public
   :store.item/created-at          public
   :store.item/index               public
   :photo/id                       public})

(defn filter-authed [db authed-user-id authed-store-ids]
  (let [filters (filter-by-attribute {:user-id  authed-user-id
                                      ;; TODO: Should check for all owned stores.
                                      :store-id (first authed-store-ids)})]
    (d/filter db (fn [db datom]
                   (if-let [filter (get filters (:ident (d/attribute db (:a datom))))]
                     (filter db datom)
                     (throw (ex-info "Filter not implemented for attribute"
                                     {:attribute (d/attribute db (:a datom))})))))))

(def schema-keys
  [
   ;; public?
   :user/email
   ;; user
   :user/verified
   ;; user
   :user/stripe
   ;; user
   :user/cart
   ;; public
   :user/profile
   ;; public
   :user.profile/name
   ;; public
   :user.profile/photo
   ;; user or store-owner
   :stripe/id
   ;; store-owner
   :stripe/publ
   ;; store-owner
   :stripe/secret
   ;; public
   :store/uuid
   ;; store-owner
   :store/stripe
   :store/owners
   :store/items
   :store/profile
   :store/sections
   :store.profile/name
   :store.profile/description
   :store.profile/return-policy
   :store.profile/tagline
   :store.profile/photo
   :store.profile/cover
   :store.section/label
   :store.section/path
   :store.owner/user
   :store.owner/role
   :store.item/uuid
   :store.item/name
   :store.item/description
   :store.item/price
   :store.item/category
   :store.item/section
   :store.item/photos
   :store.item/skus
   :store.item.photo/photo
   :store.item.photo/index
   :store.item.sku/variation
   :store.item.sku/inventory
   :store.item.sku.inventory/type
   :store.item.sku.inventory/value
   :photo/path
   :stream/title
   :stream/store
   :stream/state
   ;; store-owner
   :stream/token
   :user.cart/items
   :chat/store
   :chat/messages
   :chat.message/text
   :chat.message/user
   ;; store or user?
   :charge/id
   ;; store or user for the order. order-owner ?
   :order/charge
   :order/amount
   :order/status
   :order/store
   :order/user
   :order/uuid
   :order/shipping
   :order/items
   :order.item/parent
   :order.item/type
   :order.item/amount
   :shipping/name
   ;; user or store for the whole shipping address
   :shipping/address
   :shipping.address/street
   :shipping.address/street2
   :shipping.address/postal
   :shipping.address/locality
   :shipping.address/region
   :shipping.address/country
   :category/path
   :category/name
   :category/label
   :category/children
   :user/created-at
   :store/created-at
   :store.item/created-at
   :store.item/index
   :photo/id])