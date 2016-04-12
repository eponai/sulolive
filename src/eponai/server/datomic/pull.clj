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
                      {:report/track [{:track/functions [*]}
                                      {:track/filter [{:filter/include-tags [:tag/name]}]}]}
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
                     report-data (report/generate-data (:widget/report widget) (get-in widget [:widget/graph :graph/filter]) transactions)]
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

(defn sym-fn-seq
  "Generates an infinite seq of fn(keyword)->symbol.
  Using this instead of a direct call to gensym for testability."
  []
  (lazy-seq
    (let [sym (gensym)]
      (cons (fn [k] (symbol (str "?" (name k) "_" sym)))
           (sym-fn-seq)))))

(defn path->query
  ([[p :as path] sym sym-fns]
   (let [next-sym ((first sym-fns) p)
         query (cond-> {:where (if (= \_ (first (name p)))
                                 (let [k (keyword (namespace p) (subs (name p) 1))]
                                   [[next-sym k sym]])
                                 [[sym p next-sym]])}
                       (seq (rest path))
                       (p/merge-query (path->query (rest path) next-sym (rest sym-fns))))]
     query)))

;; Test this function too?
(defn match-path [db db-since path entity-query]
  (let [datoms (d/datoms db-since :aevt (last path))]
    (when (seq datoms)
      (let [sym-fns (sym-fn-seq)
            ;; Wrap this let into a testable function?
            query (path->query path '?e sym-fns)
            p (last path)
            last-symbol (if (= \_ (first (name p)))
                          ;; Test the edge cases of this (dec ...) and (dec (dec ...))
                          ((nth sym-fns (dec (count path))) (last path))
                          ((nth sym-fns (dec (dec (count path)))) (last path)))]
        (debug "query: " query " last-symbol: " last-symbol)
        (p/one-with db (-> entity-query
                           (p/merge-query (assoc query :symbols {[last-symbol '...] (map #(.e %) datoms)}))))))))

;; Testable?
(defn- x-since [x-with db db-since pull-pattern entity-query]
  (let [any-matches? (or (nil? db-since)
                         (->> (pull-pattern->paths pull-pattern)
                              (filter #(> (count %) 1))
                              (#(do (debug "matching paths: " (into [] %) "with pull-pattern:" pull-pattern) %))
                              (some #(match-path db db-since % entity-query))))]
    (if any-matches?
      (x-with db entity-query)
      (x-with db (p/with-db-since entity-query db-since)))))

(defn one-since [db db-since pull-pattern entity-query]
  (x-since p/one-with db db-since pull-pattern entity-query))

(defn pull-one-since [db db-since pull-pattern entity-query]
  (some->> (one-since db db-since pull-pattern entity-query)
       (p/pull db pull-pattern)))

(defn all-since [db db-since pull-pattern entity-query]
  (x-since p/all-with db db-since pull-pattern entity-query))

(defn pull-all-since [db db-since pull-pattern entity-query]
  (some->> (all-since db db-since pull-pattern entity-query)
       (p/pull-many db pull-pattern)))
