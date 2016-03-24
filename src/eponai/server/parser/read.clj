(ns eponai.server.parser.read
  (:refer-clojure :exclude [read])
  (:require
    [eponai.common.database.pull :as common.pull :refer [merge-query one-with min-by pull]]
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.format :as f]
    [eponai.common.parser :refer [read]]
    [eponai.server.datomic.pull :as p]
    [eponai.server.external.facebook :as facebook]
    [taoensso.timbre :refer [debug]]))

(defmethod read :datascript/schema
  [{:keys [db]} _ _]
  {:value (-> db
              p/schema
              eponai.datascript/schema-datomic->datascript)})

;; ############## App ################

(defmethod read :query/transactions
  [{:keys [db query auth]} _ {:keys [budget-uuid filter]}]

  (let [tx-ids (common.pull/find-transactions db {:budget-uuid  budget-uuid
                                                  :filter       filter
                                                  :query-params {:where   '[[?e :transaction/budget ?b]
                                                                            [?b :budget/created-by ?u]
                                                                            [?u :user/uuid ?user-uuid]]
                                                                 :symbols {'?user-uuid (:username auth)}}})]
    {:value (concat (common.pull/txs-with-conversions db query {:tx-ids tx-ids :user/uuid (:username auth)})
                    (common.pull/conversions db tx-ids (:username auth)))}))

;(defmethod read :query/transaction-conversions
;  [{:keys [db query auth]} _ {:keys [budget-uuid filter]}]
;  (let [tx-ids (common.pull/find-transactions db {:budget-uuid       budget-uuid
;                                                  :filter       filter
;                                                  :query-params {:where   '[[?e :transaction/budget ?b]
;                                                                            [?b :budget/created-by ?u]
;                                                                            [?u :user/uuid ?uuid]]
;                                                                 :symbols {'?uuid (:username auth)}}})
;        tx-conv-uconv (common.pull/find-conversions db tx-ids (:username auth))]
;    {:value (let [user-convs (map last tx-conv-uconv)
;                  tx-convs (map second tx-conv-uconv)]
;              (concat (common.pull/pull-many db query tx-convs)
;                      (common.pull/pull-many db query user-convs)))}))

(defmethod read :query/dashboard
  [{:keys [db auth query] :as env} _ {:keys [budget-uuid]}]

  (let [budget-with-auth (common.pull/budget-with-auth (:username auth))
        eid (if budget-uuid
              (one-with db (merge-query
                             (common.pull/budget-with-uuid budget-uuid)
                             budget-with-auth))

              ;; No budget-uuid, grabbing the one with the smallest created-at
              (min-by db :budget/created-at budget-with-auth))]

    {:value (when eid
              (let [dashboard (pull db query (one-with db {:where [['?e :dashboard/budget eid]]}))]
                (update dashboard :widget/_dashboard #(p/widgets-with-data env %))))}))

(defmethod read :query/all-budgets
  [{:keys [db query auth]} _ _]
  {:value  (common.pull/pull-many db query
                                  (common.pull/all-with db {:where   '[[?e :budget/created-by ?u]
                                                                       [?u :user/uuid ?user-uuid]]
                                                            :symbols {'?user-uuid (:username auth)}}))})

(defmethod read :query/all-currencies
  [{:keys [db query]} _ _]
  {:value  (common.pull/pull-many db query (common.pull/all-with db {:where '[[?e :currency/code]]}))})

(defmethod read :query/current-user
  [{:keys [db query auth]} _ _]
  (let [eid (one-with db {:where [['?e :user/uuid (:username auth)]]})]
    {:value (pull db query eid)}))

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