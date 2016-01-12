(ns eponai.common.database.pull
  (:require
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

(defn budgets
  [db user-uuid]
  (q '[:find [?b ...]
       :in $ ?uuid
       :where
       [?b :budget/created-by ?u]
       [?u :user/uuid ?uuid]]
     db user-uuid))

(defn all
  "takes the database, a pull query and where-clauses, where the where-clauses
  return some entity ?e."
  [db where-clauses]
  (q (vec (concat '[:find [?e ...]
                    :in $
                    :where]
                  where-clauses))
     db))