(ns eponai.common.database.pull
  (:require
    [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug trace info]]
    [eponai.common.format :as f]
    [clojure.set :as set]
    [clojure.walk :as walk]
    [datascript.db]
    #?(:clj [datomic.api :as d]
       :cljs [datascript.core :as d])
    #?(:clj
    [clj-time.core :as time]
       :cljs [cljs-time.core :as time])
    [eponai.common.parser.util :as parser])
  #?(:clj
     (:import (clojure.lang ExceptionInfo)
              (datomic.db Db))))

(defn db-instance? [db]
  #?(:clj (instance? Db db)
     :cljs (satisfies? datascript.db/IDB db)))

(defn- throw-error [e cause data]
  (let [#?@(:clj  [msg (.getMessage e)]
            :cljs [msg (.-message e)])]
    (throw (ex-info msg
                    {:cause     cause
                     :data      data
                     :message   msg
                     :exception e
                     #?@(:clj [:status :eponai.server.http/service-unavailable])}))))

(defn- do-pull [pull-fn db pattern ents]
  {:pre [(fn? pull-fn)
         (db-instance? db)
         (vector? pattern)]}
  (try
    (let [ret (pull-fn db pattern ents)]
      (if-not (= {:db/id nil}
                 ret)
        ;; Datomic returns {:db/id nil} if there's noting found for a lookup ref for example... so just return nil in that case.
        ret
        nil))
    (catch #?(:clj Exception :cljs :default) e
      (throw-error e ::pull-error {:pattern pattern
                                   :eid     ents}))))

(defn q [query & inputs]
  (try
    (apply (partial d/q query) inputs)
    (catch #?(:clj Exception :cljs :default) e
      (throw-error e ::query-error {:query  query
                                    :inputs inputs}))))

(defn pull-many [db pattern eids]
  (do-pull d/pull-many db pattern eids))

(defn pull [db pattern eid]
  {:pre [(db-instance? db)
         (vector? pattern)
         (or (number? eid) (vector? eid) (keyword? eid))]}
  (do-pull d/pull db pattern eid))


(defn- where->query [where-clauses find-pattern symbols]
  {:find find-pattern
   :in   (vec (concat '[$]
                      symbols))
   :where where-clauses})

(defn- x-with [db {:keys [find-pattern where symbols] :as entity-query}]
  {:pre [(db-instance? db)
         (or (vector? where) (seq? where))
         (or (nil? symbols) (map? symbols))]}
  (let [symbol-seq (seq symbols)
        query (where->query where
                            find-pattern
                            (map first symbol-seq))]
    (trace "query: " entity-query)
    (apply q query
       db
       (map second symbol-seq))))

(defn lookup-entity
  "Pull full entity with for the specified lookup ref. (Needs to be a unique attribute in lookup ref).

  Returns entity matching the lookupref, (nil if no lookup ref is provided or no entity exists)."
  [db lookup-ref]
  {:pre [(db-instance? db)]}
  (when lookup-ref
    (try
      (d/entity db (:db/id (pull db [:db/id] lookup-ref)))
      #?(:cljs
         (catch :default e
           (prn e)
           nil)))))

(defn one-with
  "Used the same way as all-with. Returns one entity id."
  [db params]
  {:pre [(db-instance? db)
         (map? params)]}
  (if-not (:find-pattern params)
    (x-with db (merge {:find-pattern '[?e .]} params))
    (x-with db params)))

(defn all-with
  "takes the database and a map with :where and :symbols keys.

  The value of :where is where-clauses. Ex: '[[?e :project/uuid]]

  The value of :symbols is a map of symbols in the query and
  their values. Ex: {'?uuid user-uuid}

  Returns all entities matching the symbol ?e."
  [db params]
  {:pre [(db-instance? db)
         (map? params)]}
  (if-not (:find-pattern params)
    (x-with db (merge {:find-pattern '[[?e ...]]} params))
    (x-with db params)))

(defn all
  [db query values]
  {:pre [(db-instance? db)]}

  (if (empty? values)
    (q query db)
    (apply (partial q query db) values)))

(defn merge-query
  "Preforms a merge of two query maps with :where and :symbols."
  [base addition]
  {:pre [(map? base) (map? addition)]}
  (-> base
      (update :where concat (:where addition))
      (update :symbols merge (:symbols addition))))

(defn with-db-since
  "Adds a where clause for which symbol that should be available in
  the since-db. Binds the since-db to symbol $since.

  More complicated usages are easier to just do inline."
  ([query db-since] (with-db-since query db-since '[$since ?e]))
  ([query db-since since-clause]
   (cond-> query
           (some? db-since)
           (merge-query {:where [since-clause]
                         :symbols {'$since db-since}}))))

(defn min-by [db k params]
  (some->> params
           (all-with db)
           (map #(d/entity db %))
           seq
           (apply min-key k)
           :db/id))

(defn rename-symbols
  "Takes a query map with :where clauses. Renames each where clause's symbols
    with matches from the renames map."
  [query-map renames]
  {:pre [(map? query-map) (map? renames)]}
  (-> query-map
      (update :symbols set/rename-keys renames)
      (update :where #(walk/postwalk-replace renames %))))

(defn project []
  {:where '[[?e :project/uuid]]})

(defn project-with-uuid [project-uuid]
  ;; Harder to check if it's actually a uuid.
  {:pre [(not (string? project-uuid))]}
  {:where   '[[?e :project/uuid ?project-uuid]]
   :symbols {'?project-uuid project-uuid}})

(defn with-auth [query user-uuid]
  (merge-query query
               {:where '[[?u :user/uuid ?user-uuid]]
                :symbols {'?user-uuid user-uuid}}))

(defn project-with-auth [user-uuid]
  (with-auth {:where '[[?e :project/users ?u]]} user-uuid))

(defn transaction-date-filter
  "Takes a query-map, date and a compare function symbol. Merges the query-map
  with a filter on a transaction's date.

  Examples for the compare symbol: '= '> '< '>= '>="
  [date compare]
  {:pre [(symbol? compare)]}

  (let [date-time (f/date->timestamp date)
        filter-sym (gensym "?time-filter")]
    {:where   ['[?e :transaction/date ?date]
               '[?date :date/timestamp ?timestamp]
               `[(~compare ~'?timestamp ~filter-sym)]]
     :symbols {filter-sym date-time}}))

(defn transactions
  [{:keys [filter/start-date
           filter/end-date
           filter/include-tags
           filter/exclude-tags
           filter/last-x-days] :as filter}]
  {:pre [(map? filter)]}
  (cond->
    {}
    (seq include-tags)
    (merge-query {:where   '[[?e :transaction/tags ?tag]
                             [?tag :tag/name ?include-tag]]
                  :symbols {'[?include-tag ...] (mapv :tag/name include-tags)}})

    (some? last-x-days)
    (merge-query (transaction-date-filter (time/minus (time/today) (time/days last-x-days)) '>=))

    (nil? last-x-days)
    (cond->
      (some? start-date)
      (merge-query (transaction-date-filter start-date '>=))

      (some? end-date)
      (merge-query (transaction-date-filter end-date '<=)))))

(defn verifications
  [db user-db-id status]
  {:pre [(db-instance? db)
         (number? user-db-id)
         (keyword? status)]}
  (q '[:find [?v ...]
       :in $ ?u ?s
       :where
       [?v :verification/entity ?u]
       [?u :user/email ?e]
       [?v :verification/value ?e]
       [?v :verification/status ?s]]
     db
     user-db-id
     status))

(defn transaction-entity-query [{:keys [filter project-uuid user-uuid]}]
  (assert (some? user-uuid) "User UUID must be supplied to read transactions.")
  (debug "Find transactions with filter: " filter)

  (cond-> {:where   '[[?u :user/uuid ?user-uuid]
                      [?b :project/users ?u]
                      [?e :transaction/project ?b]]
           :symbols {'?user-uuid    user-uuid}}

          (some? project-uuid)
          (merge-query {:where   '[[?b :project/uuid ?project-uuid]]
                        :symbols {'?project-uuid project-uuid}})

          (seq (keys filter))
          (merge-query (transactions filter))))

(defn find-transactions
  [db params]
  (all-with db (transaction-entity-query params)))

(defn find-latest-conversion [db {:keys [currency user] :as params}]
  ;(info "Finding latest conversion with params: " params)
  (let [query (cond currency
                    {:find-pattern '[?t ?co]
                     :symbols      {'?currency (:db/id currency)}
                     :where        '[[?co :conversion/currency ?currency]
                                     [?co :conversion/date ?d]
                                     [?d :date/timestamp ?t]]}
                    user
                    {:find-pattern '[?t ?co]
                     :symbols      {'?uuid (:user/uuid user)}
                     :where        '[[?u :user/uuid ?uuid]
                                     [?u :user/currency ?c]
                                     [?co :conversion/currency ?c]
                                     [?co :conversion/date ?d]
                                     [?d :date/timestamp ?t]]})
        data (sort (all-with db query))
        [_ conversion] (last data)]
    conversion))

;; TODO: THIS IS PROBABLY SLOW! Fix this.
(defn find-conversions [db tx-ids user-uuid]
  (->> (all-with db {:find-pattern '[?t ?e ?e2]
                 :symbols      {'[?t ...] tx-ids
                                '?uuid    user-uuid}
                 :where        '[[?t :transaction/date ?d]
                                 [?e :conversion/date ?d]
                                 [?t :transaction/currency ?cur]
                                 [?e :conversion/currency ?cur]
                                 [?u :user/uuid ?uuid]
                                 [?u :user/currency ?u-cur]
                                 [?e2 :conversion/currency ?u-cur]
                                 [?e2 :conversion/date ?d]]})
       (sequence (comp (mapcat (juxt second last))
                       (distinct)))))

(def conversion-query (parser/put-db-id-in-query
                        [{:conversion/date [:date/ymd]}
                         :conversion/rate
                         {:conversion/currency [:currency/code]}]))

(defn conversions [db tx-ids user-uuid]
  (pull-many db conversion-query (find-conversions db tx-ids user-uuid)))

(defn transaction-with-conversion-fn [db transaction-convs user-convs]
  {:pre [(delay? transaction-convs) (delay? user-convs)]}
  (let [approx-tx-conv (memoize
                         (fn [curr]
                           ;; (debug "Approximating: " curr)
                           (some->> curr (get @transaction-convs) (first) (val))))
        approx-user-curr (delay (some-> @user-convs (first) (val)))]
    (fn [transaction]
      #?(:cljs (debug "executing transaction-with-conversion on tx: " transaction))
     (let [tx-date (get-in transaction [:transaction/date :db/id])
           tx-curr (get-in transaction [:transaction/currency :db/id])
           tx-conv (get-in transaction-convs [tx-curr tx-date])
           tx-conv (or tx-conv (approx-tx-conv tx-curr))
           user-conv (get user-convs tx-date)
           user-conv (or user-conv @approx-user-curr)]
       (when (and (some? tx-conv) (some? user-conv))
         (let [;; All rates are relative USD so we need to pull what rates the user currency has,
               ;; so we can convert the rate appropriately for the user's selected currency
               user-currency-conversion (d/entity db user-conv)
               transaction-conversion (d/entity db tx-conv)
               #?@(:cljs [rate (/ (:conversion/rate transaction-conversion)
                                  (:conversion/rate user-currency-conversion))]
                   :clj  [rate (with-precision 10 (bigdec (/ (:conversion/rate transaction-conversion)
                                                             (:conversion/rate user-currency-conversion))))])]
           ;; Convert the rate from USD to whatever currency the user has set
           ;; (e.g. user is using SEK, so the new rate will be
           ;; conversion-of-transaction-currency / conversion-of-user-currency
           [(:db/id transaction)
            {:conversion/rate rate
             :conversion/date (:conversion/date transaction-conversion)}]))))))

(defn transaction-conversions [db conversion-query user-uuid transaction-entities]
  (assert (some #{:conversion/rate} conversion-query))
  ;; user currency is always the same
  ;; transaction/dates and currencies are shared across multiple transactions.
  ;; Must be possible to pre-compute some table between date<->conversion<->currency.
  (let [currency-date-pairs (delay
                              (transduce (comp (map (juxt (comp :db/id :transaction/currency)
                                                         (comp :db/id :transaction/date)))
                                              (distinct))
                                        (completing (fn [m [k v]]
                                                      {:pre [(number? k) (number? v)]}
                                                      (assoc m k (conj (get m k []) v))))
                                        {}
                                        transaction-entities))
        transaction-convs (delay
                            (->> (all-with db {:find-pattern '[?currency ?date ?conv]
                                               :symbols      {'[[?currency [?date ...]] ...] @currency-date-pairs}
                                               :where        '[[?conv :conversion/currency ?currency]
                                                               [?conv :conversion/date ?date]]})
                                 (reduce (fn [m [curr date conv]]
                                           (assoc-in m [curr date] conv))
                                         {})))

        user-curr (:db/id (:user/currency (d/entity db (one-with db {:where [['?e :user/uuid user-uuid]]}))))
        user-convs (delay
                     (when user-curr
                       (->> (all-with db {:find-pattern '[?date ?conv]
                                          :symbols      {'[?date ...] (reduce into #{} (vals @currency-date-pairs))
                                                         '?user-curr  user-curr}
                                          :where        '[[?conv :conversion/currency ?user-curr]
                                                          [?conv :conversion/date ?date]]})
                            (into {}))))]
    (into {}
      (comp (remove #(contains? % :transaction/conversion))
            (map (transaction-with-conversion-fn db transaction-convs user-convs))
            (filter some?))
          transaction-entities)))

(defn filter-excluded-tags
  ([data-filter]
   (if (:filter/exclude-tags data-filter)
     (let [filter-set (set (map #(select-keys % [:tag/name]) (:filter/exclude-tags data-filter)))]
       (filter (fn [tx]
                 (let [tag-set (set (map #(select-keys % [:tag/name]) (:transaction/tags tx)))]
                   (empty? (clojure.set/intersection filter-set tag-set))))))
     (map identity)))
  ([data-filter transactions]
   (sequence (filter-excluded-tags data-filter) transactions)))

(defn transactions-with-conversions [{:keys [db query]} user-uuid {:keys [conversion-query] :as params}]
  (let [tx-entities (sequence (comp (filter some?)
                                    (map #(d/entity db %))
                                    (filter-excluded-tags (:filter params)))
                              (find-transactions db (assoc params :user-uuid user-uuid)))
        ;; include filter-excluded-tags in the transducer and use into [] instead of sequence.
        conversions (transaction-conversions db
                                             (or conversion-query [:conversion/rate])
                                             user-uuid
                                             tx-entities)]

    (mapv #(cond-> (assoc (into {} %) :db/id (:db/id %))
                   (contains? conversions (:db/id %))
                   (assoc :transaction/conversion (get conversions (:db/id %))))
          tx-entities)))
