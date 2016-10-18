(ns eponai.server.parser.read
  (:require
    [eponai.common.database.pull :as common.pull :refer [merge-query one-with min-by pull]]
    [clojure.set :as set]
    [datomic.api :as d]
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.format :as f]
    [eponai.common.parser :refer [server-read]]
    [eponai.server.datomic.pull :as server.pull]
    [eponai.server.external.facebook :as facebook]
    [eponai.server.external.stripe :as stripe]
    [taoensso.timbre :as timbre :refer [debug trace warn]]
    [eponai.common.database.pull :as pull]
    [eponai.common.parser :as parser]
    [eponai.common.database.pull :as p]))

(defmethod server-read :datascript/schema
  [{:keys [db db-history]} _ _]
  {:value (-> (server.pull/schema db db-history)
              (eponai.datascript/schema-datomic->datascript))})

(defmethod server-read :user/current
  [{:keys [db db-history user-uuid auth]} _ _]
  (debug "read user/current with auth: " auth " user-uuid " user-uuid)
  {:value (if user-uuid
            (server.pull/one db db-history
                             [:db/id
                              :user/uuid
                              :user/email
                              {:user/status [:db/ident]}
                              {:user/currency [:db/id :currency/code]}]
                             {:where   '[[?e :user/uuid ?user-uuid]]
                              :symbols {'?user-uuid user-uuid}})
            {:error :user-not-found})})

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

(defn env+params->project-eid [{:keys [db user-uuid]} {:keys [project-eid]}]
  ;{:pre [(some? user-uuid)]}
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
          ret)))))

(defmethod parser/read-basis-param-path :query/transactions [env _ params] [(env+params->project-eid env params)])
(defmethod server-read :query/transactions
  [{:keys [db db-history query user-uuid] :as env} _ params]
  (when-let [project-eid (env+params->project-eid env params)]
    (if db-history
      (let [datom-txs (server.pull/all-datoms
                        db db-history query
                        (common.pull/transaction-entity-query {:project-eid project-eid
                                                               :user-uuid   user-uuid}))]
        {:value {:refs datom-txs}})
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
                                                      (map #(if-let [tx-conv (get conversions (:db/id %))]
                                                             ;; TODO: Do not transfer the whole conversion entity?
                                                             (assoc % :transaction/conversion tx-conv)
                                                             %)))
                                             (sort-by (comp :date/timestamp :transaction/date) > tx-entities))
                         :conversions  (d/pull-many db '[*] (seq conv-ids))
                         :refs         (d/pull-many db '[*] (seq ref-ids))})}))))

(defmethod parser/read-basis-param-path :query/dashboard [env _ params] [(env+params->project-eid env params)])
(defmethod server-read :query/dashboard
  [{:keys [db db-history query user-uuid] :as env} _ params]
  {:value (if-let [project-eid (env+params->project-eid env params)]
            (server.pull/one
              db db-history query
              (-> {:where   '[[?e :dashboard/project ?p]
                              [?p :project/users ?u]]
                   :symbols {'?p project-eid}}
                  (common.pull/with-auth user-uuid)))
            (debug "No project-eid found for user-uuid: " user-uuid))})

(defmethod parser/read-basis-param-path :query/active-dashboard [env _ params] [(env+params->project-eid env params)])
(defmethod server-read :query/active-dashboard
  [{:keys [db db-history query user-uuid] :as env} _ params]
  {:value (if-let [project-id (env+params->project-eid env params)]
            (server.pull/one db db-history query
                             (-> {:where   '[[?e :dashboard/project ?p]
                                             [?p :project/users ?u]]
                                  :symbols {'?p project-id}}
                                 (common.pull/with-auth user-uuid)))
            (debug "No project-eid found for user-uuid: " user-uuid))})

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
  {:value (let [;; TODO: uncomment this when doing settings
                ;customer (stripe/customer customer-id)
                eid (server.pull/one-changed-entity
                      db db-history query
                      {:where   '[[?u :user/uuid ?user-uuid]
                                  [?e :stripe/user ?u]]
                       :symbols {'?user-uuid user-uuid}})]

            (when eid
              (let [customer-id (:stripe/customer (d/entity db eid))
                    customer (stripe-fn :customer/get
                                        {:customer-id customer-id})]
                (debug "Read stripe customer: " customer)

                (merge {:stripe/info customer}
                       (pull db query eid)))))})

;; ############### Signup page reader #################

(defmethod server-read :query/user
  [{:keys [db db-history query]} _ {:keys [uuid]}]
  {:value (when (not (= uuid '?uuid))
            (server.pull/one db db-history query
                             {:where [['?e :user/uuid (f/str->uuid uuid)]]}))})

(defmethod server-read :query/fb-user
  [{:keys [db db-history query user-uuid]} _ _]
  (let [eid (server.pull/one-changed-entity
              db db-history query
              (-> {:where   '[[?e :fb-user/user ?u]
                              [?u :user/uuid ?uuid]]
                   :symbols {'?uuid user-uuid}}))]
    {:value (when eid
              (let [{:keys [fb-user/token fb-user/id] :as f-user}
                    (pull db [:fb-user/token :fb-user/id] eid)
                    _ (debug "Got fb-user: " f-user)
                    {:keys [name picture]} (facebook/user-info id token)]
                (merge (pull db query eid)
                       {:fb-user/name    name
                        :fb-user/picture (:url (:data picture))})))}))
