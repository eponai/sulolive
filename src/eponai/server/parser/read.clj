(ns eponai.server.parser.read
  (:require
    [eponai.common.database.pull :as common.pull :refer [merge-query one-with min-by pull]]
    [clojure.set :as set]
    [datomic.api :as d]
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.format :as f]
    [eponai.common.parser :as parser :refer [server-read]]
    [eponai.common.parser.util :as p.util]
    [eponai.server.datomic.pull :as server.pull]
    [eponai.server.external.facebook :as facebook]
    [eponai.server.external.stripe :as stripe]
    [taoensso.timbre :as timbre :refer [error debug trace warn]]
    [eponai.common.database.pull :as pull]
    [eponai.common.database.pull :as p])
  (:import (clojure.lang ExceptionInfo)))

(defmethod server-read :datascript/schema
  [{:keys [db db-history]} _ _]
  {:value (-> (server.pull/schema db db-history)
              (eponai.datascript/schema-datomic->datascript))})

(defmethod server-read :user/current
  [{:keys [db db-history user-uuid auth]} _ _]
  (debug "read user/current with auth: " auth " user-uuid " user-uuid)
  (let [error-txs [[:db.fn/retractEntity [:ui/singleton :ui.singleton/auth]]]]
    {:value (if user-uuid
              (server.pull/one-external
                db db-history
                [:db/id
                 :user/uuid
                 :user/email
                 {:user/status [:db/id :db/ident]}
                 {:user/currency [:db/id :currency/code]}]
                {:where   '[[?e :user/uuid ?user-uuid]]
                 :symbols {'?user-uuid user-uuid}}
                (fn [eid]
                  (if (p/lookup-entity db eid)
                    [{:ui/singleton           :ui.singleton/auth
                      :ui.singleton.auth/user eid}]
                    error-txs)))
              error-txs)}))

;; ############## App ################

(defn entity-map->shallow-map [e]
  (persistent!
    (reduce (fn [m [k v]]
                 (let [id (:db/id v)
                       v (cond
                           (some? id) {:db/id id}
                           (coll? v) (mapv (fn [x] {:db/id (:db/id x)}) v)
                           :else v)]
                   (assoc! m k v)))
            (transient {:db/id (:db/id e)})
            e)))

(defn env+params->project-eid [{:keys [db user-uuid]} {:keys [project]}]
  ;{:pre [(some? user-uuid)]}
  (let [project-eid (:db/id project)]
    (if (nil? user-uuid)
      (do (warn "nil user-uuid for project-eid: " project-eid)
          nil)
      (let [project-with-auth (common.pull/project-with-auth user-uuid)]
        (if project-eid
          (let [verified-eid (one-with db (merge-query project-with-auth {:symbols {'?e project-eid}}))]
            (debug "Had project eid, verifying that we have access to it.")
            (when-not (= project-eid verified-eid)
              (warn "DID NOT HAVE ACCESS TO PROJECT-EID: " project-eid " returned verified eid: " verified-eid))
            verified-eid)
          ;; No project-eid, grabbing the one with the smallest created-at
          (let [ret (min-by db :project/created-at project-with-auth)]
            (debug "fetched smallest project eid: " ret)
            ret))))))

(def conversion-attributes #{:transaction/conversion
                             :transaction/currency
                             :transaction/date
                             :transaction/fees})

(defn transaction-conversion-entities [db user-uuid tx-ids]
  (let [tx-entities (into [] (map #(d/entity db %)) tx-ids)
        conversions (pull/transaction-conversions db user-uuid tx-entities)
        conv-ids (into #{}
                       (mapcat (comp (juxt :user-conversion-id :transaction-conversion-id)
                                     val))
                       conversions)]
    (-> []
        (into (comp (map #(hash-map :db/id %))
                    (pull/assoc-conversion-xf conversions))
              tx-ids)
        (into (pull/pull-many db pull/conversion-query (seq conv-ids))))))

(defmethod parser/read-basis-param-path :query/transactions [env _ params] [(env+params->project-eid env params)])
(defmethod server-read :query/transactions
  [{:keys [db db-history query user-uuid] :as env} _ params]
  (when-let [project-eid (env+params->project-eid env params)]
    (if db-history
      (let [datom-txs (server.pull/all-datoms
                        db db-history query
                        (common.pull/transaction-entity-query {:project-eid project-eid
                                                               :user-uuid   user-uuid})
                        (fn [attr-path eavts]
                          (when (server.pull/attr-path-root? attr-path)
                            (when-let [tx-ids (seq (into #{}
                                                         (comp (filter #(contains? conversion-attributes (nth % 1)))
                                                               (map first))
                                                         eavts))]
                              (transaction-conversion-entities db user-uuid tx-ids))))
                        (fn [attr-path]
                          (when (server.pull/attr-path-root? attr-path)
                            ;; Put meta on the root datoms to be able to handle the differently.
                            (map (fn [feav] (with-meta feav {::transaction-datom true}))))))
            {transactions true others false} (group-by #(true? (::transaction-datom (meta %)))
                                                       datom-txs)]
        {:value (cond-> {}
                        (seq others)
                        (assoc :refs others)
                        (seq transactions)
                        (assoc :transactions transactions))})
      (let [entity-query (common.pull/transaction-entity-query {:project-eid project-eid
                                                                :user-uuid user-uuid})
            tx-ids (pull/all-with db entity-query)
            tx-entities (mapv #(d/entity db %) tx-ids)
            conversions (pull/transaction-conversions db user-uuid tx-entities)

            conv-ids (into #{} (mapcat (fn [[_ v]]
                                         {:pre [(:transaction-conversion-id v) (:user-conversion-id v)]}
                                         (vector (:user-conversion-id v)
                                                 (:transaction-conversion-id v))))
                           conversions)
            ref-ids (set/union
                      (server.pull/all-entities db query tx-ids)
                      (server.pull/all-entities db pull/conversion-query conv-ids))]
        {:value (cond-> {:transactions (into [] (comp (map #(entity-map->shallow-map %))
                                                      (map #(update % :transaction/type (fn [t] {:db/ident t})))
                                                      (pull/assoc-conversion-xf conversions))
                                             (sort-by (comp :date/timestamp :transaction/date) > tx-entities))
                         :conversions  (d/pull-many db '[*] (seq conv-ids))
                         :refs         (d/pull-many db '[*] (seq ref-ids))})}))))

(defmethod server-read :query/all-projects
  [{:keys [db db-history query auth]} _ _]
  {:value (if auth
            (server.pull/all db db-history query
                             {:where   '[[?e :project/users ?u]
                                         [?u :user/uuid ?user-uuid]]
                              :symbols {'?user-uuid (:username auth)}})
            (do
              (warn "No auth for :query/all-projects")
              nil))})

(defmethod server-read :query/all-currencies
  [{:keys [db db-history query]} _ _]
  {:value (server.pull/all db db-history query
                           {:where '[[?e :currency/code]]})})

(defmethod server-read :query/all-categories
  [{:keys [db db-history query] :as env} _ params]
  {:value (if-let [project-id (env+params->project-eid env params)]
            (server.pull/all db db-history query
                             {:where '[[?p :project/categories ?e]]
                              :symbols {'?p project-id}})
            (do
              (warn "No auth for :query/all-categories")))})

(defmethod server-read :query/project-users
  [{:keys [db db-history query] :as env} _ params]
  {:value (if-let [project-dbid (env+params->project-eid env params)]
            (server.pull/all db db-history query
                             {:where '[[?p :project/users ?e]]
                              :symbols {'?p project-dbid}})
            (do
              (warn "No auth for :query/all-categories")))})

(defmethod server-read :query/current-user
  [{:keys [db db-history query user-uuid auth]} _ _]
  (debug "Auth: " auth)
  (debug "User uuid " user-uuid)
  {:value (if user-uuid
            (server.pull/one db db-history query
                             {:where [['?e :user/uuid user-uuid]]})
            {:error :user-not-found})})

(defmethod server-read :query/stripe
  [{:keys [db db-history query user-uuid stripe-fn]} _ _]
  {:value (server.pull/one-external
            db db-history query
            {:where   '[[?u :user/uuid ?user-uuid]
                        [?e :stripe/user ?u]]
             :symbols {'?user-uuid user-uuid}}
            (fn [eid]
              (if-let [customer-id (:stripe/customer (d/entity db eid))]
                (when-let [customer (try
                                      (stripe-fn :customer/get
                                                 {:customer-id     customer-id
                                                  :subscription-id (get-in (d/entity db eid) [:stripe/subscription :stripe.subscription/id])})
                                      (catch ExceptionInfo e
                                        (error "Got exception from stripe call: " e)
                                        nil))]
                  (trace "Read stripe customer: " customer)
                  [{:stripe/info customer
                    :db/id       eid}])
                [[:db.fn/retractAttribute eid :stripe/info]])))})

;; ############### Signup page reader #################

(defmethod server-read :query/user
  [{:keys [db db-history query]} _ {:keys [uuid]}]
  {:value (when (not (= uuid '?uuid))
            (server.pull/one db db-history query
                             {:where [['?e :user/uuid (f/str->uuid uuid)]]}))})

(defmethod server-read :query/fb-user
  [{:keys [db db-history query user-uuid]} _ _]
  {:value (when user-uuid
            (server.pull/one-external
              db db-history query
              {:where   '[[?u :user/uuid ?uuid]
                          [?e :fb-user/user ?u]]
               :symbols {'?uuid user-uuid}}
              (fn [eid]
                (let [{:keys [fb-user/token fb-user/id]} (pull db [:fb-user/token :fb-user/id] eid)]
                  (if (and id token)
                    (let [{:keys [name picture]} (facebook/user-info id token)]
                      [{:db/id           eid
                        :fb-user/name    name
                        :fb-user/picture (:url (:data picture))}])
                    [[:db.fn/retractAttribute eid :fb-user/name]
                     [:db.fn/retractAttribute eid :fb-user/picture]])))))})
