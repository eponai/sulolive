(ns eponai.server.parser.read
  (:require
    [datomic.api :as d]
    [eponai.common.parser.read :refer [read]]
    [eponai.common.database.pull :as common.pull :refer [merge-query one-with min-by pull]]
    [eponai.server.datomic.pull :as p]
    [taoensso.timbre :refer [debug]]))

(defmethod read :query/transactions
  [{:keys [db query auth]} _ {:keys [budget-uuid filter]}]

  (let [tx-ids (common.pull/find-transactions db {:budget-uuid       budget-uuid
                                                  :filter       filter
                                                  :query-params {:where   '[[?e :transaction/budget ?b]
                                                                            [?b :budget/created-by ?u]
                                                                            [?u :user/uuid ?uuid]]
                                                                 :symbols {'?uuid (:username auth)}}})]
    {:value (let [ret (p/txs-with-conversions db
                                                 query
                                                 {:user/uuid (:username auth)
                                                  :tx-ids    tx-ids})]
              (debug "Found transactions: " (count ret) " budget: " budget-uuid)
              ret)}))

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