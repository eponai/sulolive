(ns eponai.client.parser.read
  (:require [datascript.core :as d]
            [datascript.db :as db]
            [clojure.set :as set]
            [om.next :as om]
            [om.next.impl.parser :as om.parser]
            [eponai.common.datascript :as common.datascript]
            [eponai.common.database.pull :as p]
            [eponai.common.prefixlist :as pl]
            [eponai.common.parser :refer [client-read]]
            [eponai.common.parser.util :as parser.util #?(:clj :refer :cljs :refer-macros) [timeit]]
            [eponai.common.format :as f]
            [eponai.common.parser.util :as parser]
            [eponai.client.parser.message :as message]
            [eponai.client.lib.transactions :as lib.transactions]
            #?(:cljs
               [eponai.web.ui.utils :as web-utils])
            [taoensso.timbre #?(:clj :refer :cljs :refer-macros) [info debug error trace warn]]
            [clojure.data :as diff]))

;; ################ Local reads  ####################
;; Generic, client only local reads goes here.

(defmethod client-read :query/tags
  [{:keys [db target]} _ _]
  (when-not target
    (let [tags (->> (p/find-with db '{:find-pattern [?e ?name] :where [[?e :tag/name ?name]]})
                    (mapv (fn [[id name]] {:db/id     id
                                           :tag/name  name
                                           :tag/count (count (d/datoms db :avet :transaction/tags id))}))
                    ;;(pl/prefix-list-by :tag/name)
                    )]
      {:value tags})))

;; ################ Remote reads ####################
;; Remote reads goes here. We share these reads
;; with all client platforms (web, ios, android).
;; Local reads should be defined in:
;;     eponai.<platform>.parser.read

;; ----------

(defn read-entity-by-key
  "Gets an entity by it's ref id. Returns the full component unless a pull pattern is supplied.

  Examples:
  om/IQuery
  (query [{[:ui/component :ui.component/transactions] [:db/id]}])
  om/IQuery
  (query [[:ui/singleton :ui.singleton/project]]"
  [db query key]
  (let [e (d/entity db key)]
    {:value (cond
              (nil? e) e
              query (d/pull db '[*] (:db/id e))
              :else (d/touch e))}))

(defmethod client-read :ui/component
  [{:keys [db ast query target]} _ _]
  (when-not target
    (read-entity-by-key db query (:key ast))))

(defmethod client-read :ui/singleton
  [{:keys [db ast query target]} _ _]
  (when-not target
    (read-entity-by-key db query (:key ast))))

;; --------------- Remote readers ---------------

(defmethod client-read :datascript/schema
  [_ _ _]
  {:remote true})

(defmethod client-read :user/current
  [_ _ _]
  {:remote true})

(defn sort-transactions-time-decending [transactions]
  (sort-by #(get-in % [:transaction/date :date/timestamp])
           >
           transactions))

(defn- datom-e [datom]
  (.-e datom))

(defn schema->transaction-attributes* [schema]
  (into [] (comp (filter (fn [[k _]] (= "transaction" (namespace k))))
                 (map first))
        schema))

(def schema->transaction-attributes (memoize schema->transaction-attributes*))

(defn dechunk [coll]
  (lazy-seq
    (when-let [s (seq coll)]
      (cons (first s) (rest s)))))

(let [project-transactions-cache (atom {})]
  (defn transaction-eids-by-project [db project-eid]
    {:post [(or (empty? %) (set? %))]}
    (let [{:keys [iter eids]} (get @project-transactions-cache project-eid)
          iter2 (d/datoms db :avet :transaction/project project-eid)]
      (if (common.datascript/iter-equals? iter iter2)
        eids
        (let [eids (into #{} (map datom-e) iter2)]
          (do (swap! project-transactions-cache assoc project-eid {:iter iter2 :eids eids})
              eids))))))

(defn identical-transactions?
  "Fast check whether any transaction attribute has changed in the whole database."
  [db last-db]
  (if (nil? last-db)
    false
    (->> (schema->transaction-attributes (:schema db))
         (dechunk)
         (map (fn [attr] (common.datascript/iter-equals? (d/datoms last-db :aevt attr)
                                                         (d/datoms db :aevt attr))))
         (every? true?))))

(defn transactions-changed [db last-db project-eid]
  {:pre  [(number? project-eid)]
   :post [(or (nil? %) (set? %))]}
  (when-not (identical-transactions? db last-db)
    (let [transaction-eids (transaction-eids-by-project db project-eid)
          changed-transaction-eids (cond->> transaction-eids
                                            (some? last-db)
                                            (into #{} (remove
                                                        #(common.datascript/entity-equal? db last-db %))))]
      changed-transaction-eids)))

(defn transactions-deleted [db last-db project-eid]
  {:pre  [(number? project-eid)]
   :post [(or (nil? %) (set? %))]}
  (let [last-txs (when last-db (d/datoms last-db :avet :transaction/project project-eid))
        curr-txs (d/datoms db :avet :transaction/project project-eid)]
    (when (and (seq last-txs)
               (not (common.datascript/iter-equals? last-txs curr-txs)))
      (let [last-txs (into #{} (map datom-e) last-txs)
            txs (transaction-eids-by-project db project-eid)]
        (set/difference last-txs txs)))))

(defn- tx-compare-key-fn [tx]
  ((juxt (comp :date/timestamp :transaction/date) :db/id) tx))

(defn- sorted-txs-set []
  (sorted-set-by #(compare (tx-compare-key-fn %2)
                           (tx-compare-key-fn %))))

(defn assoc-conversion-xf [conversions-by-id]
  (fn [{:keys [db/id] :as tx}]
    {:pre [(some? id)]}
    (if-let [conv (get conversions-by-id id)]
      (assoc tx :transaction/conversion conv)
      tx)))

(defn pull-many [db query eids]
  (let [is-ref? (memoize (fn [k] (and (keyword? k) (= :db.type/ref (get-in (:schema db) [k :db/valueType])))))
        parse-query (memoize
                      (fn [query]
                        (let [query (mapv #(if (is-ref? %) {% [:db/id]} %) query)
                              {refs true others false} (group-by map? query)
                              ref-attrs (into #{} (map ffirst) refs)
                              ref-queries (into {} (map seq) refs)
                              other-attrs (set others)
                              ret {:ref-attrs   ref-attrs
                                   :ref-queries ref-queries
                                   :other-attrs other-attrs}]
                          ret)))
        cardinality-many? (memoize
                            (fn [attr] (= :db.cardinality/many (get-in (:schema db) [attr :db/cardinality]))))
        seen (atom {})
        eid->map (fn self [query eid]
                   (let [{:keys [ref-attrs ref-queries other-attrs]} (parse-query query)
                         m (reduce (fn [m [_ a v]]
                                     (let [v (cond
                                               (contains? ref-attrs a)
                                               (or (get @seen v)
                                                   (self (get ref-queries a) v))
                                               (contains? other-attrs a)
                                               v
                                               :else nil)]
                                       (if (nil? v)
                                         m
                                         (if (cardinality-many? a)
                                           (update m a (fnil conj []) v)
                                           (assoc m a v)))))
                                   {:db/id eid}
                                   (d/datoms db :eavt eid))]
                     (swap! seen assoc eid m)
                     m))
        ret (into [] (map #(eid->map query %)) eids)]
    ret))

(defn all-local-transactions-by-project [{:keys [parser db txs-by-project query] :as env} project-eid]
  (let [{:keys [db-used txs]} (get @txs-by-project project-eid)
        {:keys [query/current-user]} (parser env '[{:query/current-user [:user/uuid]}])]
    (seq
      (when (and project-eid current-user)
        (if (identical? db db-used)
          txs
          (let [;; Transactions that have been deleted:
                removed-txs (or (transactions-deleted db db-used project-eid)
                                #{})
                new-txs (transactions-changed db db-used project-eid)
                _ (debug "changed-transaction-eids: " new-txs)]
            (if (and (empty? removed-txs)
                     (empty? new-txs))
              (do (swap! txs-by-project assoc-in [project-eid :db-used] db)
                  txs)
              (let [user-uuid (:user/uuid current-user)
                    tx-entities (pull-many db query new-txs)
                    new-with-convs (p/transactions-with-conversions db user-uuid tx-entities)

                    ;; Old txs may have gotten new conversions, get them.
                    old-convs (p/transaction-conversions db user-uuid txs)

                    new-and-old (cond-> (or txs (sorted-txs-set))
                                        (seq removed-txs)
                                        (#(apply disj % (map (fn [id] (d/entity db id)) removed-txs)))
                                        ;; Make space for the new ones.
                                        (seq new-with-convs)
                                        (#(apply disj % new-with-convs))
                                        ;; When there are new ones, we may have created
                                        ;; optimistic ones. Remove them.
                                        (seq new-with-convs)
                                        (#(apply disj %
                                                 (filter (fn [{:keys [db/id transaction/uuid]}]
                                                           (empty?
                                                             (d/datoms db :eavt id :transaction/uuid uuid)))
                                                         %)))
                                        (seq old-convs)
                                        (->> (into (sorted-txs-set)
                                                   (map (assoc-conversion-xf old-convs))))
                                        :always
                                        (into new-with-convs))]
                (swap! txs-by-project assoc project-eid {:db-used db :txs new-and-old})
                new-and-old))))))))

(defn active-project-eid [db]
  (or (:ui.component.project/eid (d/entity db [:ui/component :ui.component/project]))
      ;; No project-uuid, grabbing the one with the smallest created-at
      (p/min-by db :project/created-at (p/project))))

(def cached-query-transactions
  (parser.util/cache-last-read
    (fn [{:keys [db] :as env} _ p]
      {:value (p/filter-transactions p (all-local-transactions-by-project env (active-project-eid db)))})))

(defmethod client-read :query/transactions
  [{:keys [db target ast] :as env} k p]
  (if (= target :remote)
    ;; Pass the active project to remote reader
    {:remote (assoc-in ast [:params :project :db/id] (active-project-eid db))}

    ;; Local read
    (cached-query-transactions env k p)))

(defmethod client-read :query/all-projects
  [{:keys [db query target]} _ _]
  (if target
    {:remote true}
    {:value (let [auth (p/one-with db {:where '[[?e :ui/singleton :ui.singleton/auth]]})
                  {:keys [ui.singleton.auth/user]} (when auth
                                                     (p/pull db [:ui.singleton.auth/user] [:ui/singleton :ui.singleton/auth]))]
              (sort-by :project/created-at (p/pull-many db query (p/all-with db {:where [['?e :project/users (:db/id user)]]}))))}))

(defmethod client-read :query/all-currencies
  [{:keys [db query target]} _ _]
  (if target
    {:remote true}
    {:value  (p/pull-many db query (p/all-with db {:where '[[?e :currency/code]]}))}))

(defmethod client-read :query/all-tags
  [{:keys [db query target]} _ _]
  (if target
    {:remote false}
    {:value (pull-many db query (p/all-with db {:where '[[?e :tag/name]]}))}))

(defmethod client-read :query/all-categories
  [{:keys [db query target ast]} _ _]
  (if target
    {:remote (assoc-in ast [:params :project :db/id] (active-project-eid db))}
    {:value (p/pull-many db query (p/all-with db {:where '[[?e :category/name]]}))}))

(defmethod client-read :query/current-user
  [{:keys [db query target]} _ _]
  (if target
    {:remote true}
    (let [auth (p/one-with db {:where '[[?e :ui/singleton :ui.singleton/auth]]})]
      {:value (when auth
                (let [{:keys [ui.singleton.auth/user]} (p/pull db [:ui.singleton.auth/user] [:ui/singleton :ui.singleton/auth])]
                  (when (:db/id user)
                    (p/pull db query (:db/id user)))))})))

(defmethod client-read :query/stripe
  [{:keys [db query parser target] :as env} _ _]
  (if target
    {:remote #?(:cljs (not web-utils/*playground?*) :clj true)}
    (let [{:keys [query/current-user]} (parser env `[{:query/current-user [:db/id]}])
          stripe (when (:db/id current-user)
                   (p/all-with db {:where [['?e :stripe/user (:db/id current-user)]]}))]
      {:value (when stripe
                (first (p/pull-many db query stripe)))})))

;; ############ Signup page reader ############

(defmethod client-read :query/user
  [{:keys [db query target]} k {:keys [uuid]}]
  (if target
    {:remote (not (= uuid '?uuid))}
    {:value (when (and (not (= uuid '?uuid))
                       (-> db :schema :verification/uuid))
              (try
                (p/pull db query [:user/uuid (f/str->uuid uuid)])
                (catch #?@(:clj [Throwable e] :cljs [:default e])
                       (error "Error for parser's read key:" k "error:" e)
                  {:error {:cause :invalid-verification}})))}))

(defmethod client-read :query/fb-user
  [{:keys [db query target user-uuid]} _ _]
  (if target
    {:remote #?(:cljs (not web-utils/*playground?*) :clj true)}
    (let [eid (p/one-with db {:where '[[?e :fb-user/id]]})]
      {:value  (when eid
                 (p/pull db query eid))})))

(defmethod client-read :query/auth
  [{:keys [db query]} k p]
  (debug "Read " k " with params " p)
  {:value (let [auth (p/one-with db {:where '[[?e :ui/singleton :ui.singleton/auth]]})]
            (when auth
              (p/pull db query auth)))})

(defmethod client-read :query/message-fn
  [{:keys [db]} k p]
  {:value (message/get-message-fn db)})
