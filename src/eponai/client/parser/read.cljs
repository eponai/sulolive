(ns eponai.client.parser.read
  (:require [datascript.core :as d]
            [datascript.db :as db]
            [clojure.set :as set]
            [om.next :as om]
            [eponai.common.datascript :as common.datascript]
            [eponai.common.database.pull :as p]
            [eponai.common.prefixlist :as pl]
            [eponai.common.parser :refer [read]]
            [eponai.common.parser.util :as parser.util :refer-macros [timeit]]
            [eponai.common.format :as f]
            [eponai.common.parser.util :as parser]
            [eponai.client.parser.message :as message]
            [eponai.web.ui.all-transactions :as at]
            [taoensso.timbre :refer-macros [debug error]]
            [clojure.data :as diff]))

;; ################ Local reads  ####################
;; Generic, client only local reads goes here.

(defmethod read :query/tags
  [{:keys [db target]} _ _]
  (when-not target
    (let [tags (->> (p/find-with db '{:find-pattern [?e ?name] :where [[?e :tag/name ?name]]})
                    (mapv (fn [[id name]] {:db/id     id
                                           :tag/name  name
                                           :tag/count (count (d/datoms db :avet :transaction/tags id))}))
                    (pl/prefix-list-by :tag/name))]
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

(defmethod read :ui/component
  [{:keys [db ast query target]} _ _]
  (when-not target
    (read-entity-by-key db query (:key ast))))

(defmethod read :ui/singleton
  [{:keys [db ast query target]} _ _]
  (when-not target
    (read-entity-by-key db query (:key ast))))

;; --------------- Remote readers ---------------

(defmethod read :datascript/schema
  [_ _ _]
  {:remote true})

(defmethod read :user/current
  [_ _ _]
  {:remote true})

(defn sort-transactions-time-decending [transactions]
  (sort-by #(get-in % [:transaction/date :date/timestamp])
           >
           transactions))

(defn- datom-e [datom]
  (.-e datom))

(defn entity-equal? [db last-db eid]
  (common.datascript/iter-equals? (d/datoms last-db :eavt eid)
                                     (d/datoms db :eavt eid)))

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
                                            (into #{} (remove #(entity-equal? db last-db %))))]
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

(defn all-local-transactions-by-project [{:keys [parser db txs-by-project] :as env} project-eid]
  (let [{:keys [db-used txs]} (get @txs-by-project project-eid)
        {:keys [query/current-user]} (parser env '[{:query/current-user [:user/uuid]}])]
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
                  new-with-convs (p/transactions-with-conversions db user-uuid new-txs)
                  ;; Group by uuid in an atom, so we can pick transactions by id destructively.
                  new-by-uuid (atom (into {} (map #(vector (:transaction/uuid %) %)) new-with-convs))

                  ;; Old txs may have gotten new conversions, get them.
                  ;; TODO: Optimize to only do this if there are new conversions?
                  old-convs (p/transaction-conversions db user-uuid txs)

                  ;; Assoc :transaction/conversion in old tx if they need it.
                  ;; Replace new transactions in the same position they had in the cached transactions.
                  old-with-new-inserted (into [] (comp
                                                   (map (fn [{:keys [db/id] :as tx}]
                                                          {:pre [(some? id)]}
                                                          (if-let [conv (get old-convs id)]
                                                            (assoc tx :transaction/conversion conv)
                                                            tx)))
                                                   (map (fn [{:keys [transaction/uuid] :as old}]
                                                          (if-let [new (get @new-by-uuid uuid)]
                                                            (do (swap! new-by-uuid dissoc uuid)
                                                                new)
                                                            old))))
                                              txs)
                  ;; Pour the old into remaining new. We want new to be before old because
                  ;; of sorting (not sure that it is correct to do so).
                  remaining-new (vals @new-by-uuid)
                  new-and-old (cond->> old-with-new-inserted
                                       (seq remaining-new)
                                       (into (vec remaining-new)
                                             (remove #(contains? removed-txs (:db/id %))))
                                       ;; Storing the transactions time-decending because
                                       ;; it's both mobile and web's default ordering.
                                       ;; Doing this sort in our read, lets us take advantage
                                       ;; of our read caching.
                                       :always
                                       (sort-transactions-time-decending))]
              (swap! txs-by-project assoc project-eid {:db-used db :txs new-and-old})
              new-and-old)))))))

(defn active-project-eid [db]
  (or (:ui.component.project/eid (d/entity db [:ui/component :ui.component/project]))
      ;; No project-uuid, grabbing the one with the smallest created-at
      (p/min-by db :project/created-at (p/project))))

(def cached-query-transactions
  (parser.util/cache-last-read
    (fn [{:keys [db] :as env} _ p]
      {:value (p/filter-transactions p (all-local-transactions-by-project env (active-project-eid db)))})))

(defmethod read :query/transactions
  [{:keys [db target ast] :as env} k p]
  (if (= target :remote)
    ;; Pass the active project to remote reader
    {:remote (assoc-in ast [:params :project-eid] (active-project-eid db))}

    ;; Local read
    (cached-query-transactions env k p)))

(def cached-query-dashboard
  (parser.util/cache-last-read
    (fn [{:keys [db query] :as env} _ _]
      (let [project-eid (active-project-eid db)
            transactions (delay (all-local-transactions-by-project env project-eid))]

        {:value (when project-eid
                  (when-let [dashboard-id (p/one-with db {:where [['?e :dashboard/project project-eid]]})]
                    (update (p/pull db query dashboard-id)
                            :widget/_dashboard (fn [widgets]
                                                 (mapv #(p/widget-with-data db @transactions %) widgets)))))}))))

(defmethod read :query/dashboard
  [{:keys [db ast target] :as env} k p]
  (if (= target :remote)
    ;; Pass the active project to remote reader
    (let [query (om/ast->query (assoc-in ast [:params :project-eid] (active-project-eid db)))]
      ;; Add a transaction's query for the remote, so we can populate the widgets with data.
      {:remote (om/query->ast [{:proxy/dashboard [query {:query/transactions (om/get-query at/Transaction)}]}])})

    ;; Local read
    (cached-query-dashboard env k p)))

(defmethod read :query/active-dashboard
  [{:keys [db ast target query]} _ _]
  (let [project-id (active-project-eid db)]
    (if (= target :remote)
      {:remote (assoc-in ast [:params :project-eid] project-id)}

      {:value (when project-id
                (when-let [dashboard-id (p/one-with db {:where [['?e :dashboard/project project-id]]})]
                  (p/pull db query dashboard-id)))})))

(defmethod read :query/all-projects
  [{:keys [db query target]} _ _]
  (if target
    {:remote true}
    {:value (let [auth (p/one-with db {:where '[[?e :ui/singleton :ui.singleton/auth]]})
                  {:keys [ui.singleton.auth/user]} (when auth
                                                     (p/pull db [:ui.singleton.auth/user] [:ui/singleton :ui.singleton/auth]))]
              (sort-by :project/created-at (p/pull-many db query (p/all-with db {:where [['?e :project/users (:db/id user)]]}))))}))

(defmethod read :query/all-currencies
  [{:keys [db query target]} _ _]
  (if target
    {:remote true}
    {:value  (p/pull-many db query (p/all-with db {:where '[[?e :currency/code]]}))}))

(defmethod read :query/current-user
  [{:keys [db query target]} _ _]
  (if target
    {:remote true}
    (let [auth (p/one-with db {:where '[[?e :ui/singleton :ui.singleton/auth]]})]
      {:value (when auth
                (let [{:keys [ui.singleton.auth/user]} (p/pull db [:ui.singleton.auth/user] [:ui/singleton :ui.singleton/auth])]
                  (when (:db/id user)
                    (p/pull db query (:db/id user)))))})))

(defmethod read :query/stripe
  [{:keys [db query parser target] :as env} _ _]
  (if target
    {:remote true}
    (let [{:keys [query/current-user]} (parser env `[{:query/current-user [:db/id]}])
          stripe (when (:db/id current-user)
                   (p/all-with db {:where [['?e :stripe/user (:db/id current-user)]]}))]
      {:value (when stripe
                (first (p/pull-many db query stripe)))})))

;; ############ Signup page reader ############

(defmethod read :query/user
  [{:keys [db query target]} k {:keys [uuid]}]
  (if target
    {:remote (not (= uuid '?uuid))}
    {:value (when (and (not (= uuid '?uuid))
                       (-> db :schema :verification/uuid))
              (try
                (p/pull db query [:user/uuid (f/str->uuid uuid)])
                (catch :default e
                  (error "Error for parser's read key:" k "error:" e)
                  {:error {:cause :invalid-verification}})))}))

(defmethod read :query/fb-user
  [{:keys [db query target]} _ _]
  (if target
    {:remote true}
    (let [eid (p/one-with db {:where '[[?e :fb-user/id]]})]
      {:value  (when eid
                 (p/pull db query eid))
       :remote true})))

(defmethod read :query/auth
  [{:keys [db query]} k p]
  (debug "Read " k " with params " p)
  {:value (let [auth (p/one-with db {:where '[[?e :ui/singleton :ui.singleton/auth]]})]
            (when auth
              (p/pull db query auth)))})

(defmethod read :query/message-fn
  [{:keys [db]} k p]
  {:value (message/get-message-fn db)})
