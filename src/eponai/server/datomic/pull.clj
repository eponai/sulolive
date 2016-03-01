(ns eponai.server.datomic.pull
  (:require [datomic.api :as d]
            [eponai.common.database.pull :as p]
            [clojure.walk :refer [walk]]))

(defn currencies [db]
  (p/q '[:find [(pull ?e [*]) ...]
       :where [?e :currency/code]] db))

(defn schema
  "Pulls schema from the db. If data is provided includes only the necessary fields for that data.
  (type/ref, cardinality/many or unique/identity)."
  [db]
  (let [schema (p/q '[:find [?e ...]
                      :where
                      [?e :db/ident ?id]
                      [:db.part/db :db.install/attribute ?e]
                      [(namespace ?id) ?ns]
                      [(.startsWith ^String ?ns "db") ?d]
                      [(not ?d)]
                      ] db)]
    (map #(into {} (d/entity db %)) schema)))

(defn txs-with-conversions [db query {:keys [tx-ids user/uuid]}]
  (let [tx-conv-tuples (p/all-with db {:find-pattern '[?t ?e ?e2]
                                       :symbols      {'[?t ...] tx-ids
                                                      '?uuid uuid}
                                       :where        '[[?t :transaction/date ?d]
                                                       [?e :conversion/date ?d]
                                                       [?t :transaction/currency ?cur]
                                                       [?e :conversion/currency ?cur]
                                                       [?u :user/uuid ?uuid]
                                                       [?u :user/currency ?u-cur]
                                                       [?e2 :conversion/currency ?u-cur]
                                                       [?e2 :conversion/date ?d]]})
        transactions (map (fn [[tx tx-conv user-conv]]
                            (let [transaction (p/pull db query tx)
                                  ;; All rates are relative USD so we need to pull what rates the user currency has,
                                  ;; so we can convert the rate appropriately for the user's selected currency
                                  user-currency-conversion (p/pull db '[:conversion/rate] user-conv)
                                  transaction-conversion (p/pull db '[:conversion/rate] tx-conv)]
                              (assoc
                                transaction
                                :transaction/conversion
                                ;; Convert the rate from USD to whatever currency the user has set
                                ;; (e.g. user is using SEK, so the new rate will be
                                ;; conversion-of-transaction-currency / conversion-of-user-currency
                                {:conversion/rate (with-precision 10
                                                    (/ (:conversion/rate transaction-conversion)
                                                       (:conversion/rate user-currency-conversion)))})))
                          tx-conv-tuples)]
    transactions))