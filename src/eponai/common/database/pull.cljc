(ns eponai.common.database.pull
  (:require
    [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [error debug trace info warn]]
    [eponai.common.format :as f]
    [clojure.set :as set]
    [clojure.walk :as walk]
    [datascript.db]
    #?(:clj [datomic.api :as datomic])
    [datascript.core :as datascript]
    #?(:clj [clj-time.core :as time]
       :cljs [cljs-time.core :as time])
    [eponai.common.parser.util :as parser]
    [eponai.common.format.date :as date]
    [eponai.common.report :as report])
  #?(:clj
     (:import [clojure.lang ExceptionInfo]
              [datomic Connection]
              [datomic.db Db]
              [datascript.db DB])))

(defprotocol ConnectionApi
  (db* [conn]))

(extend-protocol ConnectionApi
  #?@(:clj [Connection
            (db* [conn] (datomic/db conn))
            clojure.lang.Atom
            (db* [conn] (datascript/db conn))]
      :cljs [Atom
             (db* [conn] (datascript/db conn))]))

;; Defines a common api for datascript and datomic
(defprotocol DatabaseApi
  (q* [db query args])
  (entity* [db eid])
  (pull* [db pattern eid])
  (pull-many* [db pattern eids]))

(declare do-pull)

(extend-protocol DatabaseApi
  #?@(:clj  [Db
             (q* [db query args] (apply datomic/q query db args))
             (entity* [db eid] (datomic/entity db eid))
             (pull* [db pattern eid] (do-pull datomic/pull db pattern eid))
             (pull-many* [db pattern eids] (do-pull datomic/pull-many db pattern eids))
             DB
             (q* [db query args] (apply datascript/q query db args))
             (entity* [db eid] (datascript/entity db eid))
             (pull* [db pattern eid] (do-pull datascript/pull db pattern eid))
             (pull-many* [db pattern eids] (do-pull datascript/pull-many db pattern eids))]
      :cljs [datascript.db/DB
             (q* [db query args] (apply datascript/q query db args))
             (entity* [db eid] (datascript/entity db eid))
             (pull* [db pattern eid] (do-pull datascript/pull db pattern eid))
             (pull-many* [db pattern eids] (do-pull datascript/pull-many db pattern eids))]))

(defn db-instance? [db]
  (satisfies? DatabaseApi db))

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

(defn q [query db & inputs]
  (try
    (q* db query inputs)
    (catch #?(:clj Exception :cljs :default) e
      (throw-error e ::query-error {:query  query
                                    :inputs inputs}))))

(defn pull-many [db pattern eids]
  (pull-many* db pattern eids))

(defn pull [db pattern eid]
  {:pre [(db-instance? db)
         (vector? pattern)
         (or (number? eid) (vector? eid) (keyword? eid))]}
  (pull* db pattern eid))


(defn- where->query [where-clauses find-pattern symbols]
  {:pre [(vector? find-pattern)]}
  {:find find-pattern
   :in   (vec (concat '[$]
                      symbols))
   :where where-clauses})

(defn- x-with
  ([db entity-query] (x-with db entity-query nil))
  ([db {:keys [find-pattern where symbols] :as entity-query} find]
   {:pre [(db-instance? db)
          (or (vector? where) (seq? where))
          (or (nil? symbols) (map? symbols))
          (or find-pattern find)]}
   (when (and (some? find-pattern) (some? find)
              (not= find find-pattern))
     (warn "x-with called with both find and find-pattern, and they"
           " are not equal. :find-pattern: " find-pattern
           " find: " find ". Use (find-with ...) instead of (all-with ...)"
           "or (one-with ...) when supplying your own :find-pattern."))
   (let [find-pattern (or find-pattern find)
         symbol-seq (seq symbols)
         query (where->query where
                             find-pattern
                             (map first symbol-seq))]
     (trace "query: " entity-query)
     (apply q query
            db
            (map second symbol-seq)))))

(defn lookup-entity
  "Pull full entity with for the specified lookup ref. (Needs to be a unique attribute in lookup ref).

  Returns entity matching the lookupref, (nil if no lookup ref is provided or no entity exists)."
  [db lookup-ref]
  {:pre [(db-instance? db)]}
  (when lookup-ref
    (try
      (entity* db (:db/id (pull db [:db/id] lookup-ref)))
      (catch #?@(:clj [Throwable e] :cljs [:default e])
             nil))))

(defn one-with
  "Used the same way as all-with. Returns one entity id."
  [db params]
  {:pre [(db-instance? db)
         (map? params)]}
  (x-with db params '[?e .]))

(defn all-with
  "takes the database and a map with :where and :symbols keys.

  The value of :where is where-clauses. Ex: '[[?e :project/uuid]]

  The value of :symbols is a map of symbols in the query and
  their values. Ex: {'?uuid user-uuid}

  Returns all entities matching the symbol ?e."
  [db params]
  {:pre [(db-instance? db)
         (map? params)]}
  (x-with db params '[[?e ...]]))

(defn find-with
  "Like one-with and all-with but requires to pass
  it's own find-pattern"
  [db params]
  (assert (some? (:find-pattern params))
          (str "No find-pattern for query: " params))
  (x-with db params))

(defn all
  [db query values]
  {:pre [(db-instance? db)]}

  (if (empty? values)
    (q query db)
    (apply (partial q query db) values)))

(defn merge-query
  "Preforms a merge of two query maps with :where and :symbols."
  [base {:keys [find-pattern] :as addition}]
  {:pre [(map? base) (map? addition)]}
  (-> base
      (update :where concat (:where addition))
      (update :symbols merge (:symbols addition))
      (cond-> (some? find-pattern)
              (assoc :find-pattern find-pattern))))

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
           (map #(entity* db %))
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

;; ############################# Transactions #############################

(defn transaction-entity-query [{:keys [project-eid user-uuid]}]
  (assert (some? user-uuid) "User UUID must be supplied to read transactions.")
  (assert (number? project-eid) (str "Project eid must be a number to read transactions. Was: " project-eid))
  {:where   '[[?u :user/uuid ?user-uuid]
              [?p :project/users ?u]
              [?e :transaction/project ?p]]
   :symbols {'?user-uuid user-uuid
             '?p         project-eid}})

;; TODO: This is probably slow. We want to do a separate
;;       query for transaction conversions and user
;;       conversions.
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
                           (or (some->> curr (get @transaction-convs) (first) (val))
                               (when curr
                                 (one-with db {:where [['?e :conversion/currency curr]]})))))
        approx-user-curr (memoize
                           (fn []
                             (some-> @user-convs (first) (val))))]
    (fn [transaction]
      ;;#?(:cljs (debug "executing transaction-with-conversion on tx: " (:db/id transaction)))
     (let [tx-date (get-in transaction [:transaction/date :db/id])
           curr->conv (fn [curr]
                        (or (get-in @transaction-convs [curr tx-date])
                            (approx-tx-conv curr)))
           tx-curr (get-in transaction [:transaction/currency :db/id])
           tx-conv (curr->conv tx-curr)
           user-conv (get @user-convs tx-date)
           user-conv (or user-conv (approx-user-curr))]
       (if (and (some? tx-conv) (some? user-conv))
         (let [;; All rates are relative USD so we need to pull what rates the user currency has,
               ;; so we can convert the rate appropriately for the user's selected currency
               conv->rate (fn [conv]
                            (let [transaction-conversion (entity* db conv)
                                  user-currency-conversion (entity* db user-conv)
                                  #?@(:cljs [rate (/ (:conversion/rate transaction-conversion)
                                                     (:conversion/rate user-currency-conversion))]
                                      :clj  [rate (with-precision 10 (bigdec (/ (:conversion/rate transaction-conversion)
                                                                                (:conversion/rate user-currency-conversion))))])]
                              rate))
               curr->conversion-map (fn [curr]
                                      (when curr
                                        (let [conv (curr->conv curr)
                                              rate (conv->rate conv)]
                                          {:conversion/currency {:db/id curr}
                                           :conversion/rate     rate
                                           :conversion/date     (:conversion/date (entity* db conv))})))]
           ;; Convert the rate from USD to whatever currency the user has set
           ;; (e.g. user is using SEK, so the new rate will be
           ;; conversion-of-transaction-currency / conversion-of-user-currency
           [(:db/id transaction)
            (merge (curr->conversion-map tx-curr)
                   {:user-conversion-id             user-conv
                    :transaction-conversion-id      tx-conv
                    ::currency-to-conversion-map-fn curr->conversion-map})])
         (debug "No tx-conv or user-conv. Tx-conv: " tx-conv
                " user-conv: " user-conv
                " for transaction: " (into {} transaction)))))))

(defn absolute-fee? [fee]
  #(= :transaction.fee.type/absolute (:transaction.fee/type fee)))

(defn same-conversions? [db user-curr]
  (let [user-conv->user-curr (memoize
                               (fn [user-conv]
                                 (get-in (entity* db user-conv) [:conversion/currency :db/id])))]
    (fn [tx]
      (letfn [(same-conversion? [currency conversion]
                (and (= user-curr
                        (user-conv->user-curr (:user-conversion-id conversion)))
                     (= (get-in currency [:db/id])
                        (get-in conversion [:conversion/currency :db/id]))))]
        (every? (fn [[curr conv]] (same-conversion? curr conv))
                (cons [(:transaction/currency tx)
                       (:transaction/conversion tx)]
                      (->> (:transaction/fees tx)
                           (filter absolute-fee?)
                           (map (juxt :transaction.fee/currency :transaction.fee/conversion)))))))))

(defn transaction-conversions [db user-uuid transaction-entities]
  ;; user currency is always the same
  ;; transaction/dates and currencies are shared across multiple transactions.
  ;; Must be possible to pre-compute some table between date<->conversion<->currency.
  (let [currency-date-pairs (delay
                              (transduce (comp (mapcat #(map vector
                                                             (cons (get-in % [:transaction/currency :db/id])
                                                                   (->> (:transaction/fees %)
                                                                        (map (fn [fee]
                                                                               (get-in fee [:transaction.fee/currency :db/id])))
                                                                        (filter some?)))
                                                             (repeat (get-in % [:transaction/date :db/id]))))
                                               (distinct))
                                         (completing (fn [m [k v]]
                                                       {:pre [(number? k) (number? v)]}
                                                       (assoc m k (conj (get m k []) v))))
                                         {}
                                         transaction-entities))
        transaction-convs (delay
                            (->> (find-with db {:find-pattern '[?currency ?date ?conv]
                                               :symbols      {'[[?currency [?date ...]] ...] @currency-date-pairs}
                                               :where        '[[?conv :conversion/currency ?currency]
                                                               [?conv :conversion/date ?date]]})
                                 (reduce (fn [m [curr date conv]]
                                           (assoc-in m [curr date] conv))
                                         {})))
        user-curr (one-with db {:where   '[[?u :user/uuid ?user-uuid]
                                           [?u :user/currency ?e]]
                                :symbols {'?user-uuid user-uuid}})
        user-convs (delay
                     (when user-curr
                       (let [convs (into {}
                                         (find-with db {:find-pattern '[?date ?conv]
                                                        :symbols      {'[?date ...] (reduce into #{} (vals @currency-date-pairs))
                                                                       '?user-curr  user-curr}
                                                        :where        '[[?conv :conversion/currency ?user-curr]
                                                                        [?conv :conversion/date ?date]]}))]
                         (if (seq convs)
                           convs
                           ;; When there are no conversions for any of the transaction's dates, just pick any one.
                           (let [one-conv (entity* db (one-with db {:where   '[[?e :conversion/currency ?user-curr]]
                                                                    :symbols {'?user-curr user-curr}}))]
                             {(get-in one-conv [:conversion/date :db/id]) (:db/id one-conv)})))))]
    (into {}
          ;; Skip transactions that has a conversion with the same currency.
          (comp (remove (same-conversions? db user-curr))
                (map (transaction-with-conversion-fn db transaction-convs user-convs))
                (filter some?))
          transaction-entities)))

(defn assoc-conversion-xf [conversions]
  (map (fn [tx]
         {:pre [(:db/id tx)]}
         (let [tx-conversion (get conversions (:db/id tx))
               fee-with-conv (fn [fee]
                               (if-not (absolute-fee? fee)
                                 fee
                                 (let [curr->conv (::currency-to-conversion-map-fn tx-conversion)
                                       _ (assert (fn? curr->conv))
                                       fee-conv (curr->conv (get-in fee [:transaction.fee/currency :db/id]))]
                                   (cond-> fee (some? fee-conv) (assoc :transaction.fee/conversion fee-conv)))))]
           (cond-> tx
                   (some? tx-conversion)
                   (-> (assoc :transaction/conversion (dissoc tx-conversion ::currency-to-conversion-map-fn))
                       (update :transaction/fees #(into #{} (map fee-with-conv) %))))))))

(defn transactions-with-conversions [db user-uuid tx-entities]
  (let [conversions (transaction-conversions db user-uuid tx-entities)]
    (into [] (assoc-conversion-xf conversions) tx-entities)))


;; ############################# Widgets #############################


(defn transaction-query []
  (parser/put-db-id-in-query
    '[:transaction/uuid
      :transaction/amount
      :transaction/conversion
      {:transaction/type [:db/ident]}
      {:transaction/currency [:currency/code]}
      {:transaction/tags [:tag/name]}
      {:transaction/date [*]}]))
