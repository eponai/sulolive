(ns flipmunks.budget.datomic.core
  (:require [flipmunks.budget.datomic.format :as f]
            [datomic.api :as d]))

(def conn (d/connect "datomic:dev://localhost:4334/test-budget"))

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

; Transact data to datomic
(defn post-user-tx
  "Put the user transaction maps into datomic. Will fail if one or
  more of the following required fields are not included in the map:
  #{:uuid :name :date :amount :currency}."
  [user-tx]
  (if (every? #(contains? user-tx %) #{:uuid :name :date :amount :currency})
    (d/transact conn [(f/user-tx->db-tx user-tx d/tempid :db.part/user)]) ;TODO: check if conversions exist for this date, and fetch if not.
    {:text "Missing required fields"}))                     ;TODO: fix this to pass proper error back to client.
