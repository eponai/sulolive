(ns eponai.server.datomic.pull
  (:require [datomic.api :as d]
            [eponai.server.http :as e]
            [eponai.server.datomic.validate :as v]
            [eponai.common.database.pull :as p]
            [clojure.walk :refer [walk]]
            [eponai.common.format :as f]))

; Pull and format datomic entries
(defn- tx-query
  "Create the query for pulling transactions given some date parameters."
  [params]
  (let [query '[:find [(pull ?t [*]) ...]
                :in $ ?b
                :where
                [?t :transaction/budget ?b]
                [?t :transaction/date ?d]]
        key-map {:y :date/year
                 :m :date/month
                 :d :date/day}
        map-fn (fn [[k v]] ['?d (k key-map) (Long/parseLong v)])]
    (->> key-map
         keys
         (select-keys params)
         (map map-fn)
         (apply conj query))))

(defn- user-txs [db budget-eid params]
  (when (and budget-eid (v/valid-date? params))
    (p/q (tx-query params) db budget-eid)))

(defn converted-dates [db dates]
  (p/q '[:find [?ymd ...]
       :in $ [?ymd ...]
       :where
       [?c :conversion/date ?d]
       [?d :date/ymd ?ymd]] db dates))

(defn date-conversions [db date]
  (let [conversions (p/pull db '[:conversion/_date] [:date/ymd date])]
    conversions))

(defn conversions
  "Pull conversions for the currencies and dates refered by the specified transaction ids."
  [db user-tx-ids]
  (p/q '[:find [(pull ?c [*]) ...]
       :in $ [?t ...]
       :where
       [?t :transaction/date ?d]
       [?c :conversion/date ?d]
       [?t :transaction/currency ?cur]
       [?c :conversion/currency ?cur]]
     db user-tx-ids))

(defn currencies [db]
  (p/q '[:find [(pull ?e [*]) ...]
       :where [?e :currency/code]] db))

(defn budget [db user-email]
  (p/q '[:find (pull ?e [*]) .
       :in $ ?email
       :where
       [?e :budget/created-by ?u]
       [?u :user/email ?email]] db user-email))

(defn user
  ([db email]
    (user db :user/email email))
  ([db attr value]
   (when value
     (p/q '[:find (pull ?e [*]) .
          :in $ ?a ?v
          :where [?e ?a ?v]] db attr value))))

(defn fb-user
  [db user-id]
  (p/q '[:find (pull ?e [*]) .
       :in $ ?id
       :where [?e :fb-user/id ?id]]
     db
     user-id))

(defn verification
  "Pull specific verification from the database using the unique uuid field.
  Returns an entity if onlu uuid is provided, or an entire tree matching the passed in query."
  ([db ver-uuid]
   (let [verification (verification db '[:db/id] ver-uuid)]
     (d/entity db (:db/id verification))))
  ([db query ver-uuid]
   (let [id (f/str->uuid ver-uuid)]
     (p/pull db query [:verification/uuid id]))))

(defn- schema-required?
  "Return true if the entity is required to be passed with schema.
  Is true if the entity has a type ref, or cardinality many."
  [db-entity]
  (or (= (get db-entity :db/valueType) :db.type/ref)
      (= (get db-entity :db/cardinality) :db.cardinality/many)
      (get db-entity :db/unique)))

(defn inline-value
  "Inlines values for ref attributes.
  Given entities:
  [{:db/id 1 :db/valueType {:db/id 2}}{:db/id 2 :db/ident :db.type.string}], where :db/valueType is a :db.type/ref
  and replacement-pairs:
  [[:db/valueType :db/ident]]
  will return inlined :db/valueType:
  [{:db/id 1 :db/valueType :db.type/string}{:db/id 2 :db/ident :db.type.string}]"
  [db entities replacement-pairs]
  (let [inline-attr (fn [e [a1 a2]]
                      (if (contains? e a1)
                        (assoc e a1 (->> (get-in e [a1 :db/id])
                                         (d/entity db)
                                         a2))
                        e))]
    (map (fn [e] (reduce inline-attr e replacement-pairs))
         entities)))

(defn schema
  "Pulls schema from the db. If data is provided includes only the necessary fields for that data.
  (type/ref, cardinality/many or unique/identity)."
  ([db]
    (p/q '[:find [(pull ?e [*]) ...]
         :where
         [?e :db/ident ?id]
         [:db.part/db :db.install/attribute ?e]
         [(namespace ?id) ?ns]
         [(.startsWith ^String ?ns "db") ?d]
         [(not ?d)]
         ] db))
  ([db data]
    (let [attributes (set (flatten (map keys data)))
          attribute-entities (map #(d/entity db %) attributes)]
      (vec (filter schema-required? attribute-entities)))))

(defn schema-with-inline-values [db]
  (inline-value db
                (schema db)
                [[:db/valueType :db/ident]
                 [:db/unique :db/ident]
                 [:db/cardinality :db/ident]]))
