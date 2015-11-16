(ns flipmunks.budget.datomic.pull
  (:require [datomic.api :as d]
            [flipmunks.budget.http :as e]
            [flipmunks.budget.datomic.validate :as v]
            [clojure.walk :refer [walk]]))

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

(defn- p [db pattern eid]
  (try
    (d/pull db pattern eid)
    (catch Exception e
      (throw (ex-info (.getMessage e) {:cause ::pull-error
                                       :status ::e/service-unavailable
                                       :data {:pattern pattern
                                              :eid eid}
                                       :message (.getMessage e)
                                       :exception e})))))

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
    (q (tx-query params) db budget-eid)))

(defn- distinct-values
  "Map function f over the given collection, and return a set of the distinct flattened values."
  [f coll]
  (->> coll
       (map f)
       flatten
       set))

(defn- db-entities [db user-txs attr]
  (let [distinct-entities (distinct-values attr user-txs)
        distinct-ids (map :db/id distinct-entities)]
    (map #(d/entity db %) distinct-ids)))

(defn converted-dates [db dates]
  (q '[:find [?ymd ...]
       :in $ [?ymd ...]
       :where
       [?c :conversion/date ?d]
       [?d :date/ymd ?ymd]] db dates))

(defn conversions
  "Pull conversions for the currencies and dates refered by the specified transaction ids."
  [db user-tx-ids]
  (q '[:find [(pull ?c [*]) ...]
       :in $ [?t ...]
       :where
       [?t :transaction/date ?d]
       [?c :conversion/date ?d]
       [?t :transaction/currency ?cur]
       [?c :conversion/currency ?cur]]
     db user-tx-ids))

(defn- schema-required?
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
  (let [attributes (distinct-values keys data)
        attribute-entities (map #(d/entity db %) attributes)]
    (->> attribute-entities
        (filter schema-required?)
        vec)))

(defn currencies [db]
  (q '[:find [(pull ?e [*]) ...]
       :where [?e :currency/code]] db))

(defn budget [db user-email]
  (q '[:find (pull ?e [*]) .
       :in $ ?email
       :where
       [?e :budget/created-by ?u]
       [?u :user/email ?email]] db user-email))

(defn user
  [db email]
  (q '[:find (pull ?e [*]) .
       :in $ ?m
       :where [?e :user/email ?m]] db email))

(defn password [db entity]
  (q '[:find (pull ?e [*]) .
       :in $ ?eid
       :where [?e :password/credential ?eid]] db (:db/id entity)))

(defn verifications
  "Pull verifications for the specified entity and attribute. If status is specified,
  will filter the results on that verification status."
  ([db ent attr]
    (verifications db ent attr nil))
  ([db ent attr status]
   (let [query '[:find [(pull ?ver [*]) ...]
                 :in $ ?e ?a ?v
                 :where
                 [?ver :verification/entity ?e]
                 [?e ?a ?v]]]
     (if status
       (let [query (conj query ['?ver :verification/status status])]
         (q query db (ent :db/id) attr (ent attr)))
       (q query db (ent :db/id) attr (ent attr))))))

(defn verification
  "Pull specific verification from the database using the unique uuid field."
  [db uuid]
  (p db '[*] [:verification/uuid (java.util.UUID/fromString uuid)]))

(defn nested-entities [db entity]
  (let [inner-fn second
        outer-fn #(flatten (filter coll? %))
        nested (walk inner-fn outer-fn (seq (into {} entity)))]
    (when (seq nested)
      (map #(d/entity db (:db/id %)) nested))))

(defn expand [db data]
  (loop [entities (set data)
         to-expand data]
    (if (seq to-expand)
      (if-let [nested (nested-entities db (first to-expand))]
        (recur (clojure.set/union entities (set nested))
               (concat (drop 1 to-expand) nested))
        (recur entities (drop 1 to-expand)))
      (seq entities))))

(defn all-data [db user-email params]
  (if-let [budget (budget db user-email)]
    (let [txs (user-txs db (budget :db/id) params)
          entities (partial db-entities db txs)
          attr-fn (fn [ks m] (vals (select-keys m ks)))]
      (concat (expand db txs)
              (conversions db (map :db/id txs))))
    (throw (ex-info "Invalid budget id." {:cause ::pull-error
                                          :status ::e/unprocessable-entity
                                          :message "Could not find a budget with the provided uuid."}))))

(comment
  (vec (concat
            txs
            (entities (partial attr-fn [:transaction/date
                                        :transaction/currency
                                        :transaction/tags
                                        :transaction/budget]))
            (db-entities db (entities :transaction/budget) :budget/created-by)
            (conversions db (map :db/id txs)))))