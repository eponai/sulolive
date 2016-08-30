(ns eponai.server.datomic.pull
  (:require
    [clojure.walk :as w :refer [walk]]
    [clojure.string :as string]
    [clojure.set :as set]
    [datomic.api :as d]
    [eponai.common.database.pull :as p]
    [eponai.common.parser.util :as parser]
    [eponai.common.report :as report]
    [taoensso.timbre :as timbre :refer [debug warn trace]]))

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

(defn query-matching-new-datoms-with-path [path eids path-symbols]
  {:pre [(> (count path) 1)]}
  (let [where-clauses (mapv path->where-clause
                            path
                            (partition 2 1 (cons '?e path-symbols)))
        eid-symbol (nth path-symbols (dec (dec (count path))))]
    {:where where-clauses
     :symbols {[eid-symbol '...] eids}}))

(defn x-changed-entities-in-pull-pattern [x-with db db-since path entity-query]
  "Given paths through the pull pattern (paths of refs in the pull pattern (think
   all nested maps)), check if there's an entity which matches the entity-query
   that can follow a path (via refs) which hits an entity that's changed in db-since.

   Basically: Are there any entities changed in db-since in the pull pattern of the entity-query."
  {:pre [#(> (count path) 1)]}
  (let [attr (last path)
        datom->eid (if (reverse-lookup-attr? attr) #(.v %) #(.e %))
        datoms (d/datoms db-since :aevt (normalize-attribute attr))]
    (when (seq datoms)
      (x-with db (p/merge-query entity-query
                                    (query-matching-new-datoms-with-path path (map datom->eid datoms) (sym-seq path)))))))

(defn unchunked-map
  "This version of map will only return 1 at a time.

  For performance reasons, we may only want to do some operations as
  lazy as possible. The normal clojure.core/map chunks the results,
  calculating 32 items or more each time items need to be produced.

  Test in repl:
  (defn map-test [map-f]
    (let [a (map-f #(do (prn %) %) (range 1000))]
      (seq a)
      nil))
  (map-test map)
  (map-test unchunked-map)"
  [f coll]
  (lazy-seq
    (when (seq coll)
      (cons (f (first coll))
            (unchunked-map f (rest coll))))))

(defn unchunked-filter
  [pred coll]
  (lazy-seq
    (when (seq coll)
      (let [f (first coll) r (rest coll)]
        (if (pred f)
          (cons f (unchunked-filter pred r))
          (unchunked-filter pred r))))))

(defn concat-distinct [coll colls]
  (let [distinct! ((distinct) conj!)
        unique-first (reduce distinct! (transient []) coll)]
    (persistent! (reduce (fn [all coll] (reduce distinct! all coll))
                         unique-first
                         colls))))

(defn path->paths [path]
  (->> path
       (iterate #(subvec % 0 (dec (count %))))
       (take-while seq)
       (mapv vec)))

;; Testable?
(defn- x-since [db db-since pull-pattern entity-query {:keys [x-with map-f filter-f combine]}]
  (if (nil? db-since)
    (x-with db entity-query)
    (combine (x-with db (p/with-db-since entity-query db-since))
             ;; Using delay to be sure we're lazy when its needed.
             (delay (->> pull-pattern
                         (pull-pattern->paths)
                         ;; Expand to check all possible entities in all paths.
                         ;; To think about: Can we order these in anyway to see if
                         ;;                 we should check some paths before others?
                         (mapcat path->paths)
                         (filter #(> (count %) 1))
                         (map-f #(x-changed-entities-in-pull-pattern x-with db db-since % entity-query))
                         (filter-f some?))))))

(defn one-since [db db-since pull-pattern entity-query]
  (x-since db db-since pull-pattern entity-query {:x-with   p/one-with
                                                  :map-f    unchunked-map
                                                  :filter-f unchunked-filter
                                                  :combine  (fn [entity-eid pull-eids]
                                                              (or entity-eid (first @pull-eids)))}))

(defn pull-one-since [db db-since pull-pattern entity-query]
  (some->> (one-since db db-since pull-pattern entity-query)
           (p/pull db pull-pattern)))

(defn all-since [db db-since pull-pattern entity-query]
  (x-since db db-since pull-pattern entity-query
           {:x-with  p/all-with
            :combine (fn [entity-eids pull-eidss]
                       (concat-distinct entity-eids @pull-eidss))
            :filter-f filter
            :map-f   map}))

(defn pull-all-since [db db-since pull-pattern entity-query]
  (some->> (all-since db db-since pull-pattern entity-query)
           (p/pull-many db pull-pattern)))


;; ######### x-historically

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
                                           :symbols      {'$db-history db-history}
                                           :find-pattern find-pattern}))
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
      (vector (p/merge-query {:where [db-history-clause]}
                             path-query))
      ;; Else, return queries for normal and reverse attributes.
      (let [create-query (fn [attrs where-clause]
                           (when (seq attrs)
                             (p/merge-query
                               {:where   [where-clause]
                                :symbols {'[?datom-attr-keyword ...] attrs}}
                               path-query)))
            keyword-attrs (filter keyword? attrs)
            query-attrs (create-query (remove reverse-lookup-attr? keyword-attrs)
                                      db-history-clause)
            query-reverse-attrs (create-query
                                  (filter reverse-lookup-attr? keyword-attrs)
                                  ;; Swap the e and v:
                                  (vector-swap db-history-clause 1 3))]
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
                            (p/all-with db
                                        (cond-> query
                                                (some? find-pattern)
                                                (p/merge-query {:find-pattern
                                                                find-pattern})))))))))

;; ######## History api

(defn all-datoms
  "Returns all changed datoms in tx order.
  Param: db-history is required.

  You'll want to use (all-changes ...) instead of
  this function most of the time. Only use this one
  when it's too expensive to initially use (pull-many ...)
  to get your dataset."
  [db db-history pull-pattern entity-query]
  {:pre [(some? db-history)]}
  (->> (all-changed db db-history pull-pattern entity-query)
       ;; Sort them by tx accending.
       ;; So that older tx's are transacted after the earlier ones.
       (sort-by #(nth % 3))
       (mapv (fn [[e a v _ added]]
               [(if added :db/add :db/retract) e a v]))))

(defn one [db db-history pull-pattern entity-query]
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
                              (p/one-with db
                                          (p/merge-query query
                                                         {:find-pattern '[?e .]}))))))))))

(defn all-changed-entities
  "Returns all entities thas has had something changed or has an attribute
  that can be reached with the pull pattern, changed.

  Returns all entities if db-history is nil."
  [db db-history pull-pattern entity-query]
  (if (nil? db-history)
    (p/all-with db entity-query)
    (all-changed db db-history pull-pattern entity-query '[[?e ...]])))

;; ######### All entities

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
