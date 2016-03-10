(ns eponai.server.parser.read
  (:require
    [eponai.common.parser.read :refer [read]]
    [eponai.common.database.pull :as common.pull]
    [eponai.server.datomic.pull :as pull]
    [taoensso.timbre :refer [debug]]))

(defmethod read :query/transactions
  [{:keys [db query auth]} _ {:keys [budget filter]}]

  (let [tx-ids (common.pull/find-transactions db {:budget       budget
                                                  :filter       filter
                                                  :query-params {:where   '[[?e :transaction/budget ?b]
                                                                            [?b :budget/created-by ?u]
                                                                            [?u :user/uuid ?uuid]]
                                                                 :symbols {'?uuid (:username auth)}}})]
    {:value (let [ret (pull/txs-with-conversions db
                                                 query
                                                 {:user/uuid (:username auth)
                                                  :tx-ids    tx-ids})]
              (debug "Found transactions: " (count ret) " budget: " budget)
              ret)}))