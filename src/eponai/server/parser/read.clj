(ns eponai.server.parser.read
  (:refer-clojure :exclude [read])
  (:require
    [eponai.common.database.pull :as common.pull :refer [merge-query one-with min-by pull]]
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.format :as f]
    [eponai.common.parser :refer [read]]
    [eponai.server.datomic.pull :as server.pull]
    [eponai.server.external.facebook :as facebook]
    [eponai.server.external.stripe :as stripe]
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
  (let [filters? (and (map? filter) (some #(val %) filter))
        entity-query (common.pull/transaction-entity-query
                       {:project-uuid project-uuid
                        :filter       filter
                        :query-params {:where   '[[?e :transaction/project ?b]
                                                  [?b :project/users ?u]
                                                  [?u :user/uuid ?user-uuid]]
                                       :symbols {'?user-uuid (:username auth)}}})
        tx-ids (server.pull/all-since db db-since query entity-query)]
    {:value (cond-> {:transactions (pull/pull-many db query tx-ids)
                     :conversions  (pull/conversions db tx-ids (:username auth))}
                    ;; We cannot set read-basis-t when we have filters enabled.
                    ;; To prevent read-basis-t from being set by setting it to nil.
                    filters?
                    (with-meta {:eponai.common.parser/read-basis-t nil}))}))

(defmethod read :query/dashboard
  [{:keys [db db-since auth query] :as env} _ {:keys [project-uuid]}]
  ;; TODO: Read-basis-t when using params:
  ;; https://app.asana.com/0/109022987372058/113539551736534
  (let [db-since db
        user-uuid (:username auth)
        project-with-auth (common.pull/project-with-auth user-uuid)
        project-eid (if project-uuid
              (one-with db (merge-query
                             (common.pull/project-with-uuid project-uuid)
                             project-with-auth))

              ;; No project-uuid, grabbing the one with the smallest created-at
              (min-by db :project/created-at project-with-auth))]
    {:value (when project-eid
              (let [t-p-query (server.pull/transaction-query)
                    t-e-query {:where   '[[?e :transaction/project ?project]]
                               :symbols {'?project project-eid}}
                    dashboard-entity-q (-> {:where   '[[?e :dashboard/project ?p]
                                                       [?p :project/users ?u]]
                                            :symbols {'?p project-eid}}
                                           (common.pull/with-auth user-uuid))
                    updated-transactions? (server.pull/one-since db db-since t-p-query t-e-query)
                    new-widgets? (server.pull/one-since db db-since [{:widget/_dashboard [:widget/uuid]}]
                                                        dashboard-entity-q)
                    dashboard (if updated-transactions?
                                ;; There are new transactions, so we have to update all widget's data.
                                ;; We can make this update incrementally in the future?
                                ;; Can we maybe just do this on the client?
                                ;; We'll probably need this for the playground on the client side anyway.
                                (common.pull/pull db query (common.pull/one-with db dashboard-entity-q))
                                (server.pull/pull-one-since db db-since query dashboard-entity-q))]
                (cond-> dashboard
                        ;; When there are widgets and there's either updated-transactions or widgets,
                        ;; only then should we add data to the widgets.
                        (and (contains? dashboard :widget/_dashboard)
                             (or updated-transactions? new-widgets?))
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
  {:value (let [customer-id (one-with db {:where   '[[?u :user/uuid ?user-uuid]
                                                     [?c :stripe/user ?u]
                                                     [?c :stripe/customer ?e]]
                                          :symbols {'?user-uuid (:username auth)}})
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
