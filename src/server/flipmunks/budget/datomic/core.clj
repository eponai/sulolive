(ns flipmunks.budget.datomic.core
  (:require [flipmunks.budget.datomic.format :as f]
            [datomic.api :as d])
  (:import (com.amazonaws.services.importexport.model MissingParameterException)))

; Pull and format datomic entries

(defn distinct-ids
  "Get distinct db ids from the nested entity with the given keyword k in a list of entities.
  If f is provided, it will be applied on the list of entities matching the k keyword.
  E.g. if k points to a list of values, #(apply concat %) can be provided to fetch
  the distinct values in all of the lists."
  ([entities attr]
   (mapv :db/id (set (flatten (map attr entities))))))

(defn distinct-attrs [ents]
  (->> ents
       (map keys)
       flatten
       set))

(defn db-entities
  "Get vector of entity maps for given entity ids."
  [db ids]
  (mapv #(into {:db/id %} (d/entity db %)) ids))

(defn tx-query
  "Create the query for pulling transactions given some date parameters."
  [params]
  (let [query '[:find [(pull ?t [*]) ...]
                :in $ ?uid
                :where [?uid :user/transactions ?t] [?t :transaction/date ?d]]
        key-map {:y :date/year
                 :m :date/month
                 :d :date/day}
        map-fn (fn [[k v]] ['?d (k key-map) (Long/parseLong v)])]
    (apply conj query (map map-fn (select-keys params (keys key-map))))))

(defn user-txs [db params]
  (let [user-id (params :user-id)]
    (if user-id
      (d/q (tx-query params) db (params :user-id))
      (throw (MissingParameterException. "Missing required param :user-id.")))))

(defn nested-entities [db user-txs attr]
  (db-entities db (distinct-ids user-txs attr)))

(defn all-data [db params]
  (let [user-txs (user-txs db params)
        entities (partial nested-entities db user-txs)]
    (vec (concat user-txs
                 (entities :transaction/date)
                 (entities :transaction/currency)
                 (entities :transaction/tags)))))

(defn schema
  "Pulls schema for the datomic attributes represented as keys in the given data map,
  (excludes the :db/id attribute)."
  [db data]
  (mapv #(into {} (d/entity db %)) (distinct-attrs data)))

(defn currencies [db]
  (d/q '[:find [(pull ?e [*]) ...]
         :where [?e :currency/code]] db))

(defn user [db email]
  (d/q '[:find (pull ?e [*]).
         :in $ ?m
         :where [?e :user/email ?m]] db email))

(defn schema-required?
  "Return true if the entity is required to be passed with schema.
  Is true if the entity has a type ref, or cardinality many."
  [db-entity]
  (or (= (get db-entity :db/valueType) :db.type/ref)
      (= (get db-entity :db/cardinality) :db.cardinality/many)
      (get db-entity :db/unique)))

(defn transact
  "Transact a collecion of entites into datomic."
  [conn txs]
  @(d/transact conn txs))


