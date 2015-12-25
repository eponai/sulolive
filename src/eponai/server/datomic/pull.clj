(ns eponai.server.datomic.pull
  (:require [datomic.api :as d]
            [eponai.server.http :as e]
            [eponai.server.datomic.validate :as v]
            [clojure.walk :refer [walk]]))

(defn- q [query & inputs]
  (try
    (apply (partial d/q query) inputs)
    (catch Exception e
      (println e)
      (throw (ex-info (.getMessage e)
                      {:cause ::query-error
                       :status ::e/service-unavailable
                       :data {:query query
                              :inputs inputs}
                       :message (.getMessage e)
                       :exception e})))))

(defn- p [db pattern eid]
  (try
    (d/pull db pattern eid)
    (catch Exception e
      (throw (ex-info (.getMessage e)
                      {:cause ::pull-many-error
                       :status ::e/service-unavailable
                       :data {:pattern pattern
                              :eid eid}
                       :message (.getMessage e)
                       :exception e})))))

(defn- p-many [db pattern eids]
  (try
    (d/pull-many db pattern eids)
    (catch Exception e
      (throw (ex-info (.getMessage e)
                      {:cause ::pull-error
                       :status ::e/service-unavailable
                       :data {:pattern pattern
                              :eids eids}
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

(defn expand-refs
  "Find any refs within the specified entity and expand them into entities."
  [db entity]
  (let [inner-fn second
        outer-fn #(flatten (filter coll? %))
        nested (walk inner-fn outer-fn (seq (into {} entity)))]
    (when (seq nested)
      (map #(d/entity db (:db/id %)) nested))))

(defn contains-entity? [coll entity]
  (let [eid (:db/id entity)
        eids (map :db/id coll)]
    (some #{eid} eids)))

(defn all-entities
  "Recursively expand all datomic refs in the given data into full entities and
  return a sequence of all expended entities, including the entities in the given data."
  [db data]
  (loop [entities (set data)
         expand (flatten (map #(expand-refs db %) entities))]
    (if (seq expand)
      (if (contains-entity? entities (first expand))
        (recur entities
               (rest expand))
        (let [nested (expand-refs db (first expand))]
          (recur (conj entities (first expand))
                 (concat (rest expand) nested))))
      (seq entities))))

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
    (q '[:find [(pull ?e [*]) ...]
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

(defn all
  "takes the database, a pull query and where-clauses, where the where-clauses
  return some entity ?e."
  [conn query where-clauses]
  (let [db (d/db conn)
        ents (q (vec (concat '[:find [?e ...]
                               :in $
                               :where]
                             where-clauses))
                db)]
    (p-many db query ents)))

(defn all-data
  "Pulls all user transactions and currency conversions for the dates and
  currencies used in those transactions."
  [db user-email params]
  (if-let [budget (budget db user-email)]
    (let [txs (user-txs db (budget :db/id) params)
          conversions (conversions db (map :db/id txs))]
      (vec (all-entities db (concat txs conversions))))
    (throw (ex-info "Invalid budget id." {:cause ::pull-error
                                          :status ::e/unprocessable-entity
                                          :message "Could not find a budget with the provided uuid."}))))
