(ns flipmunks.budget.datomic.core
  (:require [flipmunks.budget.datomic.format :as f]
            [datomic.api :as d]))

(def ^:dynamic conn)

; Pull and format datomic entries

(defn distinct-ids
  "Get distinct db ids from the nested entity with the given keyword k in a list of entities.
  If f is provided, it will be applied on the list of entities matching the k keyword.
  E.g. if k points to a list of values, #(apply concat %) can be provided to fetch
  the distinct values in all of the lists."
  ([k entities]
   (distinct-ids nil k entities))
  ([f k entities]
   (mapv :db/id (distinct ((or f identity) (mapv k entities))))))

(defn db-entities
  "Get vector of entity maps for given entity ids."
  [ids]
  (mapv #(into {:db/id %} (d/entity (d/db conn) %)) ids))

(defn get-query
  "Create the query for pulling transactions given some date parameters."
  [params]
  (let [query '[:find [(pull ?e [*]) ...]
                :where [?e :transaction/date ?d]]
        key-map {:y :date/year
                 :m :date/month
                 :d :date/day}]
    (apply conj query (map (fn [[k v]] ['?d (k key-map) (Long/parseLong v)]) params))))

(defn pull-data
  "Pull transaction data for optional given y, m, d parameters."
  [params]
  (let [transactions (d/q (get-query params) (d/db conn))]
    (vec (concat transactions
                 (db-entities (distinct-ids :transaction/date transactions))
                 (db-entities (distinct-ids :transaction/currency transactions))
                 (db-entities (distinct-ids #(apply concat %) :transaction/tags transactions))))))

(defn pull-schema
  "Pulls schema for the datomic attributes represented as keys in the given data map,
  (excludes the :db/id attribute)."
  [data]
  (let [idents (set (apply concat (mapv keys data)))]
    (mapv #(into {} (d/entity (d/db conn) %)) (disj idents :db/id))))

(defn schema-required?
  "Return true if the entity is required to be passed with schema.
  Is true if the entity has a type ref, or cardinality many."
  [db-entity]
  (or (= (get db-entity :db/valueType) :db.type/ref)
      (= (get db-entity :db/cardinality) :db.cardinality/many)
      (get db-entity :db/unique)))

(defn transact-data
  "Transact data into datomic. format-fn should take three arguments, 1st the data
  to be transformed, 2nd the function to user to generate tempi db ids and 3rd
  the partition for the db ids will be called on data to transform into appropriate
  datomic entities."
  [format-fn data]
  (let [transactions (format-fn data d/tempid :db.part/user)]
    (d/transact conn transactions)))


