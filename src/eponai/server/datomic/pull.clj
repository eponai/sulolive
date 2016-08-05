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
  ([db db-since]
   (let [query (-> {:where '[[?e :db/ident ?id]
                             [:db.part/db :db.install/attribute ?e]
                             [(namespace ?id) ?ns]
                             [(.startsWith ^String ?ns "db") ?d]
                             [(not ?d)]]}
                   (p/with-db-since db-since))]
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
  {:pre [(keyword? attr)]}
  (string/starts-with? (name attr) "_"))

(defn normalize-attribute [attr]
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
  (-> v
      (assoc i1 (nth v i2))
      (assoc i2 (nth v i1))))

(defn changed-datoms-matching-path
  "Finds all changed datoms matching the pull-pattern.

  For all changed datoms in db-history using the attrs in the pull-pattern,
  find entities "
  [db db-history entity-query {:keys [path attrs]}]
  (let [path-syms (cons '?e (sym-seq path))
        return-eid (or (last path-syms) '?e)
        path-where-clauses (map path->where-clause path (partition 2 1 path-syms))
        find-pattern [return-eid
                      '?datom-attr-keyword
                      '?datom-value
                      '?datom-tx
                      '?datom-added]
        path-query (cond-> entity-query
                           (seq path-where-clauses)
                           (p/merge-query {:where (vec path-where-clauses)})
                           :always
                           (p/merge-query {:where '[[?datom-attr
                                                     :db/ident
                                                     ?datom-attr-keyword]]
                                           :symbols {'$db-hist db-history}
                                           :find-pattern find-pattern}))
        db-hist-datoms ['$db-hist return-eid '?datom-attr '?datom-value '?datom-tx '?datom-added]
        changed-datoms-by-attrs (fn [attrs where-clause]
                                  (when (seq attrs)
                                    (p/all-with db
                                                (p/merge-query
                                                  {:where   [where-clause]
                                                   :symbols {'[?datom-attr-keyword ...] attrs}}
                                                  path-query))))]
    (concat (changed-datoms-by-attrs (remove reverse-lookup-attr? attrs)
                                     db-hist-datoms)
            (changed-datoms-by-attrs (filter reverse-lookup-attr? attrs)
                                     (vector-swap db-hist-datoms 1 3)))))

;; TODO: TEST THIS :D

(defn pattern->attr-paths
  "Given a pull-pattern, return maps of {:path [] :attrs []} where:
  :path is the path into the pull pattern
  :attrs are all the keys at the current path.

  Example:
  (pattern->attr-paths [:abc {:foo [:bar {:baz [:abc]}]}])
  Returns:
  [{:path [], :attrs (:abc :foo)}
   {:path [:foo], :attrs (:bar :baz)}
   {:path [:foo :baz], :attrs (:abc)}]
  Explanation:
  At path [], including the join, we've got attrs :abc and :foo."
  ([pattern] (pattern->attr-paths pattern []))
  ([pattern path]
   (let [ks (into [] (comp (map #(cond (keyword? %) %
                                       (map? %) (ffirst %)))
                           (remove #(= :db/id %)))
                  pattern)
         joins (filter map? pattern)]
     (into [{:path path :attrs ks}]
           (mapcat (fn [m]
                     {:pre (= 1 (count m))}
                     (let [[k v] (first m)]
                       (pattern->attr-paths v (conj path k)))))
           joins))))

(defn datoms-since [db db-history pull-pattern entity-query]
  (->> pull-pattern
       (pattern->attr-paths)
       (into [] (mapcat (fn [attr-path]
                          (changed-datoms-matching-path
                            db db-history entity-query attr-path))))))

(defn datom-txs-since [db db-history pull-pattern entity-query]
  (->> (datoms-since db db-history pull-pattern entity-query)
       ;; Sort them by tx accending.
       ;; So that older tx's are transacted after the earlier ones.
       (sort-by #(nth % 3))
       (mapv (fn [[e a v _ added]]
               [(if added :db/add :db/retract) e a v]))))

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
