(ns eponai.common.database.pull
  (:require
    [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [debug]]
    #?(:clj
            [datomic.api :as d]
       :cljs [datascript.core :as d])))

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
  (do-pull d/pull db pattern eid))

(defn verifications
  [db user-db-id status]
  {:pre [(number? user-db-id)
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

(defn transactions
  [db user-uuid]
  (q '[:find [?t ...]
       :in $ ?uuid
       :where
       [?t :transaction/budget ?b]
       [?b :budget/created-by ?u]
       [?u :user/uuid ?uuid]]
     db user-uuid))

(defn- where->query [where-clauses find-pattern symbols]
  (vec (concat '[:find] find-pattern
               '[:in $] symbols
               [:where]
               where-clauses)))

(defn- x-with [db find-pattern {:keys [where symbols]}]
  {:pre [(or (vector? where) (seq? where))
         (or (nil? symbols) (map? symbols))]}
  (let [symbol-seq (seq symbols)
        query (where->query where
                            find-pattern
                            (map first symbol-seq))]
    (apply q query
       db
       (map second symbol-seq))))

(defn one-with
  "Used the same way as all-with. Returns one entity id."
  [db params]
  (x-with db '[?e .] params))

(defn all-with
  "takes the database and a map with :where and :symbols keys.

  The value of :where is where-clauses. Ex: '[[?e :budget/uuid]]

  The value of :symbols is a map of symbols in the query and
  their values. Ex: {'?uuid user-uuid}

  Returns all entities matching the symbol ?e."
  [db params]
  (x-with db '[[?e ...]] params))

(defn all
  [db query values]
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
  (update query-map :where (fn [where]
                             (mapv (fn [v] (mapv #(get renames % %) v))
                                   where))))

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