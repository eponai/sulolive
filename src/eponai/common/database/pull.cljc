(ns eponai.common.database.pull
  (:require
    [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug trace]]
    [eponai.common.format :as f]
    [clojure.set :as s]
    [clojure.walk :as walk]
    #?(:clj
    [datomic.api :as d]
       :cljs [datascript.core :as d]))
  #?(:clj
     (:import (clojure.lang ExceptionInfo)
              (datomic.db Db))))

(defn db-instance? [db]
  (instance? #?(:clj  Db
                :cljs datascript.db/DB) db))

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

(defn- x-with [db {:keys [find-pattern where symbols]}]
  {:pre [(db-instance? db)
         (or (vector? where) (seq? where))
         (or (nil? symbols) (map? symbols))]}
  (let [symbol-seq (seq symbols)
        query (where->query where
                            find-pattern
                            (map first symbol-seq))]
    (trace "query:" query)
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

  The value of :where is where-clauses. Ex: '[[?e :budget/uuid]]

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

(defn rename-symbols
  "Takes a query map with :where clauses. Renames each where clause's symbols
    with matches from the renames map."
  [query-map renames]
  {:pre [(map? query-map) (map? renames)]}
  (-> query-map
      (update :symbols s/rename-keys renames)
      (update :where #(walk/postwalk-replace renames %))))

(defn budget []
  {:where '[[?e :budget/uuid]]})

(defn budget-with-filter [budget-uuid]
  ;; Harder to check if it's actually a uuid.
  {:pre [(not (string? budget-uuid))]}
  {:where   '[[?e :budget/uuid ?budget-uuid]]
   :symbols {'?budget-uuid budget-uuid}})

(defn budget-with-auth [user-uuid]
  {:where   '[[?e :budget/created-by ?u]
              [?u :user/uuid ?user-uuid]]
   :symbols {'?user-uuid user-uuid}})

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
  [{:keys [filter/start-date filter/end-date filter/include-tags] :as filter}]
  {:pre [(map? filter)]}
  (cond->
    {}
    (not-empty include-tags)
    (merge-query {:where   '[[?e :transaction/tags ?tag]
                             [?tag :tag/name ?tag-name]]
                  :symbols {'[?tag-name ...] include-tags}})

    (some? start-date)
    (merge-query (transaction-date-filter start-date '>=))

    (some? end-date)
    (merge-query (transaction-date-filter end-date '<=))))

(defn conversions [tx-ids]
  {:find-pattern '[?t ?e]
   :symbols      {'[?t ...] tx-ids}
   :where        '[[?t :transaction/date ?d]
                   [?e :conversion/date ?d]
                   [?t :transaction/currency ?cur]
                   [?e :conversion/currency ?cur]]})

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