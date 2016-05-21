(ns eponai.server.parser.read
  (:refer-clojure :exclude [read])
  (:require
    [eponai.common.database.pull :as common.pull :refer [merge-query one-with min-by pull]]
    [clojure.set :as set]
    [datomic.api :as d]
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.format :as f]
    [eponai.common.parser :refer [read]]
    [eponai.server.datomic.pull :as server.pull]
    [eponai.server.external.facebook :as facebook]
    [eponai.server.external.stripe :as stripe]
    [taoensso.timbre :refer [debug trace]]
    [eponai.common.database.pull :as pull]
    [eponai.common.parser :as parser]))

(defmethod read :datascript/schema
  [{:keys [db db-since]} _ _]
  {:value (-> (server.pull/schema db db-since)
              (eponai.datascript/schema-datomic->datascript))})

(defmethod read :user/current
  [{:keys [db db-since user-uuid]} _ _]
  {:value (when user-uuid
            (server.pull/pull-one-since db db-since [:db/id :user/uuid {:user/currency [:db/id :currency/code]}]
                                        {:where   '[[?e :user/uuid ?user-uuid]]
                                         :symbols {'?user-uuid user-uuid}}))})

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
  {:pre [(some? user-uuid)]}
  (let [project-with-auth (common.pull/project-with-auth user-uuid)]
    (if project-eid
      (one-with db (merge-query project-with-auth {:symbols {'?e project-eid}}))
      ;; No project-eid, grabbing the one with the smallest created-at
      (min-by db :project/created-at project-with-auth))))

(defmethod parser/read-basis-param-path :query/transactions [env _ params] [(env+params->project-eid env params)])
(defmethod read :query/transactions
  [{:keys [db db-since query user-uuid] :as env} _ params]
  (let [project-eid (env+params->project-eid env params)
        entity-query (common.pull/transaction-entity-query {:project-eid project-eid :user-uuid user-uuid})
        tx-ids (server.pull/all-since db db-since query entity-query)
        tx-entities (mapv #(d/entity db %) tx-ids)
        conversions (pull/transaction-conversions db user-uuid tx-entities)

        conv-ids (into #{} (mapcat (fn [[_ v]]
                                     {:pre [(:transaction-conversion-id v) (:user-conversion-id v)]}
                                     (vector (:user-conversion-id v)
                                             (:transaction-conversion-id v))))
                       conversions)
        ref-ids (set/union
                  (server.pull/all-entities db query tx-ids)
                  (server.pull/all-entities db pull/conversion-query conv-ids))
        pull-xf (map #(d/pull db '[*] %))]
    {:value (cond-> {:transactions (into [] (comp (map #(entity-map->shallow-map %))
                                                  (map #(update % :transaction/type (fn [t] {:db/ident t})))
                                                  (map #(if-let [tx-conv (get conversions (:db/id %))]
                                                         ;; TODO: Do not transfer the whole conversion entity?
                                                         (assoc % :transaction/conversion tx-conv)
                                                         %)))
                                         tx-entities)
                     :conversions  (into [] pull-xf conv-ids)
                     :refs         (into [] pull-xf ref-ids)})}))

(defmethod parser/read-basis-param-path :query/dashboard [env _ params] [(env+params->project-eid env params)])
(defmethod read :query/dashboard
  [{:keys [db db-since query user-uuid] :as env} _ params]
  {:value (if-let [project-eid (env+params->project-eid env params)]
            (server.pull/pull-one-since db db-since query
                                        (-> {:where   '[[?e :dashboard/project ?p]
                                                        [?p :project/users ?u]]
                                             :symbols {'?p project-eid}}
                                            (common.pull/with-auth user-uuid)))
            (debug "No project-eid found for user-uuid: " user-uuid))})

(defmethod read :query/all-projects
  [{:keys [db db-since query auth]} _ _]
  {:value (server.pull/pull-all-since db db-since query
                                      {:where   '[[?e :project/users ?u]
                                                  [?u :user/uuid ?user-uuid]]
                                       :symbols {'?user-uuid (:username auth)}})})

(defmethod read :query/all-currencies
  [{:keys [db db-since query]} _ _]
  {:value (server.pull/pull-all-since db db-since query
                                      {:where '[[?e :currency/code]]})})

(defmethod read :query/current-user
  [{:keys [db db-since query user-uuid]} _ _]
  {:value (server.pull/pull-one-since db db-since query {:where [['?e :user/uuid user-uuid]]})})

(defmethod read :query/stripe
  [{:keys [db db-since query user-uuid]} _ _]
  {:value (let [customer-id (one-with db {:where   '[[?u :user/uuid ?user-uuid]
                                                     [?c :stripe/user ?u]
                                                     [?c :stripe/customer ?e]]
                                          :symbols {'?user-uuid user-uuid}})
                ;_ (debug "Found customer id: " customer-id)
                ;TODO: uncomment this when doing settings
                customer {}                                 ;(stripe/customer customer-id)
                ]
            (assoc
              (common.pull/pull db query [:stripe/customer customer-id])
              :stripe/info
              customer)
            ;(server.pull/pull-all-since db db-since query
            ;                            {:where ['[?e :stripe/user ?u]
            ;                                     ['?u :user/uuid (:username auth)]]})
            )})

;; ############### Signup page reader #################

(defmethod read :query/user
  [{:keys [db query]} _ {:keys [uuid]}]
  {:value (when (not (= uuid '?uuid))
            (pull db query [:user/uuid (f/str->uuid uuid)]))})

(defmethod read :query/fb-user
  [{:keys [db db-since query user-uuid]} _ _]
  (let [eid (server.pull/one-since db db-since query
                                   (-> {:where   '[[?e :fb-user/user ?u]
                                                   [?u :user/uuid ?uuid]]
                                        :symbols {'?uuid user-uuid}}))]
    {:value (when eid
              (let [{:keys [fb-user/token
                            fb-user/id]} (pull db [:fb-user/token :fb-user/id] eid)
                    {:keys [name picture]} (facebook/user-info id token)]
                (merge (pull db query eid)
                       {:fb-user/name    name
                        :fb-user/picture (:url (:data picture))})))}))
