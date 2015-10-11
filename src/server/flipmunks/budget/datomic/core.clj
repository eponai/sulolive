(ns flipmunks.budget.datomic.core
  (:require [flipmunks.budget.datomic.format :as f]
            [datomic.api :as d]))

; Pull and format datomic entries

(defn distinct-ids
  "Get distinct db ids from the nested entity with the given keyword k in a list of entities.
  If f is provided, it will be applied on the list of entities matching the k keyword.
  E.g. if k points to a list of values, #(apply concat %) can be provided to fetch
  the distinct values in all of the lists."
  ([k entities]
   (distinct-ids nil k entities))
  ([f k entities]
   (mapv :db/id (distinct (flatten (mapv k entities))))))

(defn db-entities
  "Get vector of entity maps for given entity ids."
  [ids db]
  (mapv #(into {:db/id %} (d/entity db %)) ids))

(defn tx-query
  "Create the query for pulling transactions given some date parameters."
  [params]
  (let [query '[:find [(pull ?e [*]) ...]
                :where [?e :transaction/date ?d]]
        key-map {:y :date/year
                 :m :date/month
                 :d :date/day}]
    (apply conj query (map (fn [[k v]] ['?d (k key-map) (Long/parseLong v)]) params))))

(defn pull-user-txs [params db]
  (d/q (tx-query params) db))

(defn pull-entites [attr user-txs db]
  (db-entities (distinct-ids attr user-txs) db))

(defn pull-data [params db]
  (let [user-txs (pull-user-txs params db)]
    (vec (concat user-txs
                 (pull-entites :transaction/date user-txs db)
                 (pull-entites :transaction/currency user-txs db)
                 (pull-entites :transaction/tags user-txs db)))))

(defn pull-schema
  "Pulls schema for the datomic attributes represented as keys in the given data map,
  (excludes the :db/id attribute)."
  [data db]
  (let [idents (set (apply concat (mapv keys data)))]
    (mapv #(into {} (d/entity db %)) (disj idents :db/id))))

(defn pull-currencies [db]
  (d/q '[:find [(pull ?e [*]) ...]
         :where [?e :currency/code]] db))

(defn schema-required?
  "Return true if the entity is required to be passed with schema.
  Is true if the entity has a type ref, or cardinality many."
  [db-entity]
  (or (= (get db-entity :db/valueType) :db.type/ref)
      (= (get db-entity :db/cardinality) :db.cardinality/many)
      (get db-entity :db/unique)))

(defn transact-data
  "Transact a collecion of entites into datomic."
  [conn txs]
  (d/transact conn txs))


