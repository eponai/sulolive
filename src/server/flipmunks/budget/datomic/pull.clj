(ns flipmunks.budget.datomic.pull
  (:require [datomic.api :as d]
            [flipmunks.budget.http :as e]))

(defn- q [query & inputs]
  (try
    (apply (partial d/q query) inputs)
    (catch Exception e
      (throw (ex-info (.getMessage e) {:cause ::query-error
                                       :status ::e/service-unavailable
                                       :data {:query query
                                              :inputs inputs}
                                       :message (.getMessage e)
                                       :exception e})))))

; Pull and format datomic entries
(defn- distinct-values
  "Map function f over the given collection, and return a set of the distinct flattened values."
  [coll f]
  (->> coll
       (map f)
       flatten
       set))

(defn- tx-query
  "Create the query for pulling transactions given some date parameters."
  [params]
  (let [query '[:find [(pull ?t [*]) ...]
                :in $ ?email
                :where [?u :user/email ?email]
                [?u :user/transactions ?t]
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

(defn user-txs [db user-email params]
  (when user-email
    (q (tx-query params) db user-email)))

(defn- db-entities [db user-txs attr]
  (let [distinct-ids (map :db/id (distinct-values
                                   user-txs
                                   attr))]
    (map #(into {:db/id %} (d/entity db %)) distinct-ids)))

(defn converted-dates [db dates]
  (q '[:find [?ymd ...]
       :in $ [?ymd ...]
       :where
       [?c :conversion/date ?d]
       [?d :date/ymd ?ymd]] db dates))

(defn conversions [db user-tx-ids]
  (q '[:find [(pull ?c [*]) ...]
       :in $ [?t ...]
       :where
       [?t :transaction/date ?d]
       [?c :conversion/date ?d]
       [?t :transaction/currency ?cur]
       [?c :conversion/currency ?cur]]
     db user-tx-ids))

(defn all-data [db user-email params]
  (let [user-txs (user-txs db user-email params)
        entities (partial db-entities db user-txs)]
    (vec (concat user-txs
                 (entities :transaction/date)
                 (entities :transaction/currency)
                 (entities :transaction/tags)
                 (conversions db (map :db/id user-txs))))))

(defn schema-required?
  "Return true if the entity is required to be passed with schema.
  Is true if the entity has a type ref, or cardinality many."
  [db-entity]
  (or (= (get db-entity :db/valueType) :db.type/ref)
      (= (get db-entity :db/cardinality) :db.cardinality/many)
      (get db-entity :db/unique)))

(defn schema
  "Pulls schema for the datomic attributes represented as keys in the given data map,
  (excludes the :db/id attribute)."
  [db data]
  (vec (filter schema-required? (map #(into {} (d/entity db %)) (distinct-values data keys)))))

(defn currencies [db]
  (q '[:find [(pull ?e [*]) ...]
         :where [?e :currency/code]] db))

(defn user [db email]
  (q '[:find (pull ?e [*]).
         :in $ ?m
         :where [?e :user/email ?m]] db email))



