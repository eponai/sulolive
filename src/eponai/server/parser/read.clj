(ns eponai.server.parser.read
  (:refer-clojure :exclude [read])
  (:require
    [eponai.common.database.pull :as common.pull :refer [merge-query one-with min-by pull]]
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.format :as f]
    [eponai.common.parser :refer [read]]
    [eponai.server.datomic.pull :as p]
    [eponai.server.external.facebook :as facebook]
    [taoensso.timbre :refer [debug]]
    [eponai.common.database.pull :as pull]))

(defmethod read :datascript/schema
  [{:keys [db]} _ _]
  {:value (-> db
              p/schema
              eponai.datascript/schema-datomic->datascript)})

(defmethod read :user/current
  [{:keys [db auth]} _ _]
  {:value (when (:username auth)
            (common.pull/pull db [:db/id :user/uuid] [:user/uuid (:username auth)]))})

;; ############## App ################

(defmethod read :query/transactions
  [{:keys [db query auth]} _ {:keys [project-uuid filter]}]
  (let [tx-ids (pull/all-with db {:where '[[?e :transaction/uuid]]})
        _ (common.pull/find-transactions db {:project-uuid  project-uuid
                                                  :filter       filter
                                                  :query-params {:where   '[[?e :transaction/project ?b]
                                                                            [?b :project/users ?u]
                                                                            [?u :user/uuid ?user-uuid]]
                                                                 :symbols {'?user-uuid (:username auth)}}})]
    {:value {:transactions (pull/pull-many db query tx-ids)
             :conversions (pull/conversions db tx-ids (:username auth))}}))

(defmethod read :query/dashboard
  [{:keys [db auth query] :as env} _ {:keys [project-uuid]}]

  (let [project-with-auth (common.pull/project-with-auth (:username auth))
        eid (if project-uuid
              (one-with db (merge-query
                             (common.pull/project-with-uuid project-uuid)
                             project-with-auth))

              ;; No project-uuid, grabbing the one with the smallest created-at
              (min-by db :project/created-at project-with-auth))]

    {:value (when eid
              (let [dashboard (pull db query (one-with db {:where [['?e :dashboard/project eid]]}))]
                (update dashboard :widget/_dashboard #(p/widgets-with-data env eid %))))}))

(defmethod read :query/all-projects
  [{:keys [db query auth]} _ _]
  {:value  (common.pull/pull-many db query
                                  (common.pull/all-with db {:where   '[[?e :project/users ?u]
                                                                       [?u :user/uuid ?user-uuid]]
                                                            :symbols {'?user-uuid (:username auth)}}))})

(defmethod read :query/all-currencies
  [{:keys [db query]} _ _]
  {:value  (common.pull/pull-many db query (common.pull/all-with db {:where '[[?e :currency/code]]}))})

(defmethod read :query/current-user
  [{:keys [db query auth]} _ _]
  (let [eid (one-with db {:where [['?e :user/uuid (:username auth)]]})]
    {:value (pull db query eid)}))

(defmethod read :query/stripe
  [{:keys [db query auth]} _ _]
  (let [stripe-eid (common.pull/all-with db {:where [['?e :stripe/user [:user/uuid (:username auth)]]]})]
    {:value (common.pull/pull-many db query stripe-eid)}))

;; ############### Signup page reader #################

(defmethod read :query/user
  [{:keys [db query]} _ {:keys [uuid]}]
  {:value (when (not (= uuid '?uuid))
            (pull db query [:user/uuid (f/str->uuid uuid)]))})

(defmethod read :query/fb-user
  [{:keys [db query auth]} _ _]
  (let [eid (one-with db {:where '[[?e :fb-user/user ?u]
                                   [?u :user/uuid ?uuid]]
                          :symbols {'?uuid (:username auth)}})]
    {:value (when eid
              (let [{:keys [fb-user/token
                            fb-user/id]} (pull db [:fb-user/token :fb-user/id] eid)
                    {:keys [name picture]} (facebook/user-info id token)]
                (merge (pull db query eid)
                       {:fb-user/name    name
                        :fb-user/picture (:url (:data picture))})))}))