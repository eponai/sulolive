(ns eponai.server.datomic.pull
  (:require
    [clojure.walk :as w :refer [walk]]
    [clojure.string :as string]
    [clojure.set :as set]
    [datomic.api :as d]
    [eponai.common.database.pull :as p]
    [eponai.common.parser.util :as parser]
    [eponai.common.report :as report]
    [taoensso.timbre :as timbre :refer [info debug warn trace]]))

(defn currencies [db]
  (p/q '[:find [(pull ?e [*]) ...]
       :where [?e :currency/code]] db))

(defn schema
  "Pulls schema from the db. If data is provided includes only the necessary fields for that data.
  (type/ref, cardinality/many or unique/identity)."
  ([db] (schema db nil))
  ([db db-history]
   (let [query (cond-> {:where '[[?e :db/ident ?id]
                                 [:db.part/db :db.install/attribute ?e]
                                 [(namespace ?id) ?ns]
                                 [(.startsWith ^String ?ns "db") ?d]
                                 [(not ?d)]]}
                       (some? db-history)
                       (p/merge-query {:where   '[[$db-history ?e]]
                                       :symbols {'$db-history db-history}}))]
     (mapv #(into {} (d/entity db %))
           (p/all-with db query)))))

;(defn dates-between [db start end]
;  (p/pull-many db [:date/timestamp] (p/all-with db {:where '[[?e :date/timestamp ?time]
;                                                            [(<= ?start ?time)]
;                                                            [(<= ?time ?end)]]
;                                                    :symbols {'?start start
;                                                              '?end end}})))

(defn new-currencies [db rates]
  (let [currency-codes (mapv #(get-in % [:conversion/currency :currency/code]) rates)
        currencies (p/pull-many db [:currency/code] (p/all-with db {:where   '[[?e :currency/code ?code]]
                                                                    :symbols {'[?code ...] currency-codes}}))
        db-currency-codes (map :currency/code currencies)
        new-currencies (clojure.set/difference (set currency-codes) (set db-currency-codes))]
    (debug "Found new currencies: " new-currencies)
    new-currencies))


;; ######### All entities

(declare reverse-lookup-attr? normalize-attribute)

;; Pull pattern stuff

(defn keep-refs-only
  "Given a pull pattern, keeps only the refs (maps).

  Example:
  [:db/id
   {:conversion/date [:db/id :date/ymd]}
   :conversion/rate
   {:conversion/currency [:db/id :currency/code]}]
   => retruns =>
   [{:conversion/date [:date/ymd]}
    {:conversion/currency [:currency/code]}]"
  [pattern]
  (letfn [(keep-maps [x]
            (if (vector? x)
              (filterv map? x)
              (filter map? x)))]
    (w/prewalk (fn [x]
                 (cond
                   (map-entry? x)
                   (let [v (val x)
                         v (if (some map? v)
                             (keep-maps v)
                             (some-> (some #(when (not= :db/id %) %) v)
                                     (vector)
                                     (with-meta {::keep true})))]
                     [(key x) v])
                   (and (sequential? x) (not (::keep (meta x))))
                   (keep-maps x)

                   :else x))
               pattern)))

;; Generate tests for this?
;; Something with (get-in paths) or something.
(defn pull-pattern->paths
  ([pattern] (pull-pattern->paths pattern []))
  ([pattern prev]
   (cond
     (keyword? pattern)
     (when (not= pattern :db/id)
       [(conj prev pattern)])

     (map? pattern)
     (let [[k v] (first pattern)]
       (recur v (conj prev k)))

     (sequential? pattern)
     (if (symbol? (first pattern))
       (recur (second pattern) prev)
       (sequence (comp (mapcat #(pull-pattern->paths % prev))
                       (filter some?))
                 pattern))
     :else nil)))

(defn eid->refs [db eid attr]
  (if (reverse-lookup-attr? attr)
    (map :e (d/datoms db :vaet eid (normalize-attribute attr)))
    (map :v (d/datoms db :eavt eid attr))))

(defn eids->refs [db eids attr]
  {:post [(set? %)]}
  (into #{} (mapcat (fn [e] (eid->refs db e attr)))
        eids))

(defn path+eids->refs [db eids path]
  {:post [(set? %)]}
  (loop [eids eids path path ret #{}]
    (if (and (seq eids) (seq (rest path)))
      (let [attr (first path)
            refs (eids->refs db eids attr)]
        (recur refs (rest path) (into ret refs)))
      ret)))

(defn all-entities [db pull-pattern eids]
  {:post [(set? %)]}
  (->> pull-pattern
       (keep-refs-only)
       (pull-pattern->paths)
       (map (fn [path]
              {:post [(or (nil? %) (set? %))]}
              (path+eids->refs db eids path)))
       (apply set/union)))

;; ######### x-historically

(defn sym-seq
  "Generates symbols. Is passed around for testability."
  [path]
  (map #(gensym (str "?" (name %) "_")) path))

(defn reverse-lookup-attr? [attr]
  {:pre [(or (keyword? attr) (= '* attr))]}
  (string/starts-with? (name attr) "_"))

(defn normalize-attribute [attr]
  {:pre [(or (keyword? attr) (= '* attr))]}
  (if (reverse-lookup-attr? attr)
    (keyword (namespace attr) (subs (name attr) 1))
    attr))

(defn- path->where-clause
  [attr [sym next-sym]]
  (let [k (normalize-attribute attr)]
    (if (reverse-lookup-attr? attr)
      [next-sym k sym]
      [sym k next-sym])))

(defn vector-swap
  "Swaps the values for index i1 and i2 in vector v."
  [v i1 i2]
  {:pre [(vector? v)]}
  (-> v
      (assoc i1 (nth v i2))
      (assoc i2 (nth v i1))))

(defn- changed-path-queries [db-history entity-query {:keys [path attrs]}]
  {:pre [(some? db-history)]}
  (let [path-syms (cons '?e (sym-seq path))
        return-eid (last path-syms)
        ;; Create a path of where-clauses from entity ?e through
        ;; the path of the pull-pattern.
        path-where-clauses (map path->where-clause
                                path
                                (partition 2 1 path-syms))
        find-pattern [return-eid
                      '?datom-attr-keyword
                      '?datom-value
                      '?datom-tx
                      '?datom-added]
        attribute-number-to-keyword-clause '[?datom-attr-number
                                             :db/ident
                                             ?datom-attr-keyword]
        path-query (cond-> entity-query
                           (seq path-where-clauses)
                           (p/merge-query {:where (vec path-where-clauses)})
                           :always
                           (p/merge-query {:where        [attribute-number-to-keyword-clause]
                                           :symbols      {'$db-history db-history}}))
        db-history-clause (-> find-pattern
                              ;; Replace:
                              ;; ?datom-attr-keyword
                              ;; with
                              ;; ?datom-attr-number
                              (assoc 1 '?datom-attr-number)
                              ;; Look up in db-history
                              (->> (cons '$db-history))
                              (vec))]
    (assert (every? #(or (keyword? %) (= '* %)) attrs)
            (str "Attributes in path: " path " were not only"
                 " keywords or '*, were: " attrs))
    ;; If we've got a star attribute,
    ;; just return the query matching everything.
    (if (some #(= '* %) attrs)
      ;; For a "star" attribute, just don't specify
      ;; which values the ?datom-attr-keyword can take.
      (vector (p/merge-query {:where [db-history-clause]
                              :find-pattern find-pattern}
                             path-query))
      ;; Else, return queries for normal and reverse attributes.
      (let [create-query (fn [attrs where-clause find-pattern]
                           (when (seq attrs)
                             (->> path-query
                                  (p/merge-query
                                    {:where        [where-clause]
                                     :symbols      {'[?datom-attr-keyword ...] attrs}
                                     :find-pattern find-pattern}))))
            attr-in-db-history? (fn [attr]
                                  ;; This prevents queries to be run on ALL attributes.
                                  ;; TODO: Profile this filter, to see if it scales.
                                  (seq (d/datoms db-history :aevt (normalize-attribute attr))))
            keyword-attrs (->> attrs
                               (filter keyword?)
                               (filter attr-in-db-history?))
            query-attrs (create-query (remove reverse-lookup-attr? attrs)
                                      db-history-clause
                                      find-pattern)
            query-reverse-attrs (create-query (->> keyword-attrs
                                                   (filter reverse-lookup-attr?)
                                                   (map normalize-attribute))
                                              ;; Swap the e and v for reverse attrs
                                              (vector-swap db-history-clause 1 3)
                                              (vector-swap find-pattern 0 2))]
        (filter some? [query-attrs query-reverse-attrs])))))

;; TODO: TEST THIS :D

(defn- pattern->attr-paths
  "Given a pull-pattern, return maps of {:path [] :attrs []} where:
  :path is the path into the pull pattern
  :attrs are all the keys at the current path.

  Example:
  (pattern->attr-paths '[:abc * {:foo [:bar {:baz [:abc]}]}])
  Returns:
  [{:path [], :attrs (:abc '* :foo)}
   {:path [:foo], :attrs (:bar :baz)}
   {:path [:foo :baz], :attrs (:abc)}]
  Explanation:
  At path [], including the join, we've got attrs :abc and :foo."
  ([pattern] (pattern->attr-paths pattern []))
  ([pattern path]
   (let [ks (into [] (comp (map (fn [x]
                                  {:post [(some? %)]}
                                  (cond (keyword? x) x
                                        (map? x) (ffirst x)
                                        (= '* x) x)))
                           (remove #(= :db/id %)))
                  pattern)
         joins (filter map? pattern)]
     (into [{:path path :attrs ks}]
           (mapcat (fn [join]
                     {:pre (= 1 (count join))}
                     (let [[k v] (first join)]
                       (pattern->attr-paths v (conj path k)))))
           joins))))

(defn- all-changed
  "Finds all changed datoms matching the pull-pattern.

  For all changed datoms in db-history using an entity-query and the
  paths and attrs of a pull-pattern.

  By defaults uses a find-pattern that gets datoms [e a v tx added],
  but can be customized by passing a find-pattern parameter."
  [db db-history pull-pattern entity-query & [find-pattern]]
  {:pre [(or (nil? find-pattern)
             (vector? find-pattern))]}
  (->> pull-pattern
       (pattern->attr-paths)
       (into [] (comp
                  (mapcat (fn [attr-path]
                            (changed-path-queries db-history entity-query attr-path)))
                  (mapcat (fn [query]
                            (let [query (cond-> query
                                                (some? find-pattern)
                                                (p/merge-query {:find-pattern find-pattern}))
                                  ret (p/find-with (d/history db) query)]
                              ret)))))))


;; ######## History api

(defn- datoms->txs [datoms]
  (->> datoms
       ;; Sort datoms by tx number, so that earlier transactions
       ;; get applied first.
       (sort-by #(nth % 3))
       (mapv (fn [[e a v _ added]]
               [(if added :db/add :db/retract) e a v]))))

(defn all-datoms
  "Returns all changed datoms in tx order.
  Param: db-history is required.

  You'll want to use (all-changes ...) instead of
  this function most of the time. Only use this one
  when it's too expensive to initially use (pull-many ...)
  to get your dataset."
  [db db-history pull-pattern entity-query]
  {:pre [(some? db-history)]}
  (let [datoms (->> (all-changed db db-history pull-pattern entity-query)
                    (datoms->txs))]
    (trace "For entity-query: " entity-query " returning datoms: " datoms)
    datoms))

(defn one
  "Initially gets an entity and uses (pull ...) on it with
  the pull-pattern. Gets all changed datoms when db-history is
  available."
  [db db-history pull-pattern entity-query]
  (if (nil? db-history)
    (p/pull db pull-pattern (p/one-with db entity-query))
    (all-datoms db db-history pull-pattern entity-query)))

(defn all
  "Initially gets everything by using (pull-many ...), then
  gets all datoms that have been changed, by using the entity
  query and the pull-pattern."
  [db db-history pull-pattern entity-query]
  (if (nil? db-history)
    (p/pull-many db pull-pattern (p/all-with db entity-query))
    (all-datoms db db-history pull-pattern entity-query)))

(defn one-changed-entity
  "Returns an entity id which has been changed or if something has changed
  that can be reached in the pull pattern."
  [db db-history pull-pattern entity-query]
  {:post [(or (nil? %)
              (number? %))]}
  (if (nil? db-history)
    (p/one-with db entity-query)
    (->> pull-pattern
         (pattern->attr-paths)
         (some (fn [attr-path]
                 (->> (changed-path-queries db-history entity-query attr-path)
                      (some (fn [query]
                              (let [q (p/merge-query query
                                                     {:find-pattern '[?e .]})]
                                (p/one-with db q))))))))))

(defn all-changed-entities
  "Returns all entities thas has had something changed or has an attribute
  that can be reached with the pull pattern, changed.

  Returns all entities if db-history is nil."
  [db db-history pull-pattern entity-query]
  (if (nil? db-history)
    (p/all-with db entity-query)
    (all-changed db db-history pull-pattern entity-query '[[?e ...]])))

(defn adds-retracts-for-eid [db db-history eid]
  (let [attr-id->keyword (memoize #(:db/ident (d/entity db %)))]
    (->> (d/datoms db-history :eavt eid)
         (map (fn [[e a v t added]] [e (attr-id->keyword a) v t added]))
         (datoms->txs))))