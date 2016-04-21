(ns eponai.server.datomic.pull
  (:require
    [clojure.walk :refer [walk]]
    [datomic.api :as d]
    [eponai.common.database.pull :as p]
    [eponai.common.parser.util :as parser]
    [eponai.common.report :as report]
    [taoensso.timbre :refer [debug warn]]))

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

(defn widget-report-query []
  (parser/put-db-id-in-query
    '[:widget/uuid
     :widget/width
     :widget/height
     {:widget/filter [{:filter/include-tags [:tag/name]}]}
     {:widget/report [:report/uuid
                      {:report/track [{:track/functions [*]}]}
                      {:report/goal [*
                                     {:goal/cycle [*]}]}
                      :report/title]}
     :widget/index
     :widget/data
     {:widget/graph [:graph/style]}]))

(defn transaction-query []
  (parser/put-db-id-in-query
    [:transaction/uuid
     :transaction/amount
     :transaction/conversion
     {:transaction/currency [:currency/code]}
     {:transaction/tags [:tag/name]}
     {:transaction/date [:date/ymd
                         :date/timestamp]}]))

;(defn dates-between [db start end]
;  (p/pull-many db [:date/timestamp] (p/all-with db {:where '[[?e :date/timestamp ?time]
;                                                            [(<= ?start ?time)]
;                                                            [(<= ?time ?end)]]
;                                                    :symbols {'?start start
;                                                              '?end end}})))

(defn widgets-with-data [{:keys [db auth] :as env} project-eid widgets]
  (->> widgets
       (mapv (fn [{:keys [widget/uuid]}]
               (let [widget (p/pull db (widget-report-query) [:widget/uuid uuid])
                     project (p/pull db [:project/uuid] project-eid)
                     transactions (p/transactions-with-conversions
                                    (assoc env :query (transaction-query))
                                    (:username auth)
                                    {:filter       (:widget/filter widget)
                                     :project-uuid (:project/uuid project)})
                     timestamps (map #(:date/timestamp (:transaction/date %)) transactions)
                     report-data (report/generate-data (:widget/report widget) transactions {:data-filter (get-in widget [:widget/graph :graph/filter])})]
                 (assoc widget :widget/data report-data))))))

(defn new-currencies [db rates]
  (let [currency-codes (mapv #(get-in % [:conversion/currency :currency/code]) rates)
        currencies (p/pull-many db [:currency/code] (p/all-with db {:where   '[[?e :currency/code ?code]]
                                                                    :symbols {'[?code ...] currency-codes}}))
        db-currency-codes (map :currency/code currencies)
        new-currencies (clojure.set/difference (set currency-codes) (set db-currency-codes))]
    (debug "Found new currencies: " new-currencies)
    new-currencies))

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
  (= \_ (first (name attr))))

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
   all nested maps))), check if there's an entity which matches the entity-query
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

;; Testable?
(defn- x-since [db db-since pull-pattern entity-query {:keys [x-with map-f filter-f combine]}]
  (if (nil? db-since)
    (x-with db entity-query)
    (combine (x-with db (p/with-db-since entity-query db-since))
             (->> (pull-pattern->paths pull-pattern)
                  (filter #(> (count %) 1))
                  (map-f #(x-changed-entities-in-pull-pattern x-with db db-since % entity-query))
                  (filter-f some?)))))

(defn one-since [db db-since pull-pattern entity-query]
  (x-since db db-since pull-pattern entity-query {:x-with   p/one-with
                                                  :map-f    unchunked-map
                                                  :filter-f unchunked-filter
                                                  :combine  (fn [entity-eid pull-eids]
                                                              (or entity-eid (first pull-eids)))}))

(defn pull-one-since [db db-since pull-pattern entity-query]
  (some->> (one-since db db-since pull-pattern entity-query)
           (p/pull db pull-pattern)))

(defn all-since [db db-since pull-pattern entity-query]
  (x-since db db-since pull-pattern entity-query
           {:x-with  p/all-with
            :combine (fn [entity-eids pull-eidss]
                       (concat-distinct entity-eids pull-eidss))
            :filter-f filter
            :map-f   map}))

(defn pull-all-since [db db-since pull-pattern entity-query]
  (some->> (all-since db db-since pull-pattern entity-query)
           (p/pull-many db pull-pattern)))
