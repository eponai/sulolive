(ns eponai.client.parser.read
  (:require [datascript.core :as d]
            [eponai.common.database.pull :as p]
            [eponai.common.parser :refer [read]]
            [eponai.common.parser.util :as parser.util]
            [taoensso.timbre :refer-macros [debug error]]
            [eponai.common.format :as f]))

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
  [{:keys [db ast query]} _ _]
  (read-entity-by-key db query (:key ast)))

(defmethod read :ui/singleton
  [{:keys [db ast query]} _ _]
  (read-entity-by-key db query (:key ast)))

;; --------------- Remote readers ---------------

(defmethod read :datascript/schema
  [_ _ _]
  {:remote true})

(defmethod read :user/current
  [_ _ _]
  {:remote true})

(defn transactions-since [db last-db project-eid]
  {:pre [(number? project-eid)]
   :post [(set? %)]}
  (let [last-basis (:max-tx last-db)

        new-transaction-eids (into #{} (comp (mapcat #(d/datoms db :eavt (.-e %)))
                                             (filter #(> (.-tx %) last-basis))
                                             (map #(.-e %)))
                                   (d/datoms db :avet :transaction/project project-eid))]
    (debug "new-transaction-eids: " (count new-transaction-eids))
    new-transaction-eids))

(defn distinct-by
  "Like clojure.core/distinct, but items will be distinct by f applied to each item."
  ([f] (distinct-by f #{}))
  ([f seen-init]
   (fn [rf]
     (let [seen (volatile! seen-init)]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result input]
          (if (contains? @seen (f input))
            result
            (do (vswap! seen conj (f input))
                (rf result input)))))))))

(def txs-by-project (atom {}))

(defn all-local-transactions-by-project [{:keys [parser db] :as env} project-eid]
  (assert (number? project-eid) (str "Project-eid was not a number. Was: " project-eid))
  (let [{:keys [db-used txs]} (get @txs-by-project project-eid)
        {:keys [query/current-user]} (parser env '[{:query/current-user [:user/uuid]}])]
    (when (and project-eid current-user)
      (if (= db db-used)
        txs
        (let [new-txs (transactions-since db db-used project-eid)
              new-with-convs (vec (p/transactions-with-conversions db (:user/uuid current-user) new-txs))
              new-and-old (into new-with-convs
                                (distinct-by :transaction/uuid (into #{} (map :transaction/uuid) new-with-convs))
                                txs)]
          (swap! txs-by-project assoc project-eid {:db-used db :txs new-and-old})
          new-and-old)))))

(defn active-project-eid [db]
  (or (:ui.component.project/eid (d/entity db [:ui/component :ui.component/project]))
      ;; No project-uuid, grabbing the one with the smallest created-at
      (p/min-by db :project/created-at (p/project))))

(defmethod read :query/transactions
  [{:keys [db target ast] :as env} k p]
  (let [project-eid (active-project-eid db)
        ;; TODO: Pass project-eid instead of project-uuid to remote.
        project-uuid (:project/uuid (d/entity db project-eid))]
    (if (= target :remote)
      ;; Pass the active project uuid to remote reader
      {:remote (assoc-in ast [:params :project-uuid] project-uuid)}

      ;; Local read
      {:value (p/filter-transactions p (all-local-transactions-by-project env project-eid))})))

(defmethod read :query/dashboard
  [{:keys [db ast query target] :as env} k p]
  (let [project-eid (active-project-eid db)
        ;; TODO: Pass project-eid instead of project-uuid to remote.
        project-uuid (:project/uuid (d/entity db project-eid))]
    (if (= target :remote)
      ;; Pass the active project uuid to remote reader
      {:remote (assoc-in ast [:params :project-uuid] project-uuid)}

      ;; Local read
      (let [transactions (delay (all-local-transactions-by-project env project-eid))]

        {:value (when project-eid
                  (when-let [dashboard-id (p/one-with db {:where [['?e :dashboard/project project-eid]]})]
                    (update (p/pull db query dashboard-id)
                            :widget/_dashboard (fn [widgets]
                                                 (mapv #(cond->> %
                                                                 (nil? (:widget/data %))
                                                                 (p/widget-with-data db @transactions))
                                                       widgets)))))}))))

(defmethod read :query/all-projects
  [{:keys [db query]} _ _]
  {:value  (sort-by :project/created-at (p/pull-many db query (p/all-with db (p/project))))
   :remote true})

(defmethod read :query/all-currencies
  [{:keys [db query]} _ _]
  {:value  (p/pull-many db query (p/all-with db {:where '[[?e :currency/code]]}))
   :remote true})

(defmethod read :query/current-user
  [{:keys [db query]} _ _]
  (let [{:keys [ui.singleton.auth/user]} (p/pull db [:ui.singleton.auth/user] [:ui/singleton :ui.singleton/auth])]
    {:value  (when (:db/id user)
               (p/pull db query (:db/id user)))
     :remote true}))

(defmethod read :query/stripe
  [{:keys [db query parser] :as env} _ _]
  (let [{:keys [query/current-user]} (parser env `[{:query/current-user [:db/id]}])
        stripe (when (:db/id current-user)
                 (p/all-with db {:where [['?e :stripe/user (:db/id current-user)]]}))]
    {:value  (when stripe
               (first (p/pull-many db query stripe)))
     :remote true}))

;; ############ Signup page reader ############

(defmethod read :query/user
  [{:keys [db query]} k {:keys [uuid]}]
  {:value  (when (and (not (= uuid '?uuid))
                      (-> db :schema :verification/uuid))
             (try
               (p/pull db query [:user/uuid (f/str->uuid uuid)])
               (catch :default e
                 (error "Error for parser's read key:" k "error:" e)
                 {:error {:cause :invalid-verification}})))
   :remote (not (= uuid '?uuid))})

(defmethod read :query/fb-user
  [{:keys [db query]} _ _]
  (let [eid (p/one-with db {:where '[[?e :fb-user/id]]})]
    {:value  (when eid
               (p/pull db query eid))
     :remote true}))