(ns eponai.server.parser.read
  (:refer-clojure :exclude [read])
  (:require
    [eponai.common.database.pull :as common.pull :refer [merge-query one-with min-by pull]]
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.format :as f]
    [eponai.common.parser :refer [read]]
    [eponai.server.datomic.pull :as server.pull]
    [eponai.server.external.facebook :as facebook]
    [taoensso.timbre :refer [debug]]
    [eponai.common.database.pull :as pull]))

(defmethod read :datascript/schema
  [{:keys [db db-since]} _ _]
  {:value (-> (server.pull/schema db db-since)
              (eponai.datascript/schema-datomic->datascript))})

(defmethod read :user/current
  [{:keys [db db-since auth]} _ _]
  {:value (when (:username auth)
            (server.pull/pull-one-since db db-since [:db/id :user/uuid]
                                        {:where   '[[?e :user/uuid ?user-uuid]]
                                         :symbols {'?user-uuid (:username auth)}}))})

;; ############## App ################

(defmethod read :query/transactions
  [{:keys [db db-since query auth]} _ {:keys [project-uuid filter]}]
  (let [tx-ids (common.pull/find-transactions db
                                              {:project-uuid project-uuid
                                               :filter       filter
                                               :query-params (-> {:where   '[[?e :transaction/project ?b]
                                                                             [?b :project/users ?u]
                                                                             [?u :user/uuid ?user-uuid]]
                                                                  :symbols {'?user-uuid (:username auth)}}
                                                                 (common.pull/with-db-since db-since))})]
    {:value {:transactions (pull/pull-many db query tx-ids)
             :conversions (pull/conversions db tx-ids (:username auth))}}))

(defmethod read :query/dashboard
  [{:keys [db db-since auth query] :as env} _ {:keys [project-uuid]}]

  (let [user-uuid (:username auth)
        project-with-auth (common.pull/project-with-auth user-uuid)
        project-eid (if project-uuid
              (one-with db (merge-query
                             (common.pull/project-with-uuid project-uuid)
                             project-with-auth))

              ;; No project-uuid, grabbing the one with the smallest created-at
              (min-by db :project/created-at project-with-auth))]
    {:value (when project-eid
              (let [t-p-query (server.pull/transaction-query)
                    t-e-query{:where   '[[(= ?project ?project-eid)]
                                [?project :project/uuid]
                                [?e :transaction/project ?project]]
                     :symbols {'?project-eid project-eid}}
                    updated-transactions? (server.pull/one-since db db-since t-p-query t-e-query)
                    _ (debug "Updated transaction: " updated-transactions?
                             "pull-all-since: " (server.pull/pull-all-since db db-since t-p-query t-e-query))
                    dashboard (server.pull/pull-one-since db db-since query (-> {:where   '[[?e :dashboard/project ?p]
                                                                                            [?p :project/users ?u]]
                                                                                 :symbols {'?p project-eid}}
                                                                                (common.pull/with-auth user-uuid)))]
                (cond-> dashboard
                        updated-transactions?
                        (update :widget/_dashboard #(server.pull/widgets-with-data env project-eid %)))))}))

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
  [{:keys [db db-since query auth]} _ _]
  {:value (server.pull/pull-one-since db db-since query {:where [['?e :user/uuid (:username auth)]]})})

(defmethod read :query/stripe
  [{:keys [db db-since query auth]} _ _]
  {:value (server.pull/pull-all-since db db-since query
                                      {:where ['[?e :stripe/user ?u]
                                               ['?u :user/uuid (:username auth)]]})})

;; ############### Signup page reader #################

(defmethod read :query/user
  [{:keys [db query]} _ {:keys [uuid]}]
  {:value (when (not (= uuid '?uuid))
            (pull db query [:user/uuid (f/str->uuid uuid)]))})

(defmethod read :query/fb-user
  [{:keys [db db-since query auth]} _ _]
  (let [eid (server.pull/one-since db db-since query
                                   (-> {:where   '[[?e :fb-user/user ?u]
                                                   [?u :user/uuid ?uuid]]
                                        :symbols {'?uuid (:username auth)}}))]
    {:value (when eid
              (let [{:keys [fb-user/token
                            fb-user/id]} (pull db [:fb-user/token :fb-user/id] eid)
                    {:keys [name picture]} (facebook/user-info id token)]
                (merge (pull db query eid)
                       {:fb-user/name    name
                        :fb-user/picture (:url (:data picture))})))}))
