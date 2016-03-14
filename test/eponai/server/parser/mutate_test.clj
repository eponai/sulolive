(ns eponai.server.parser.mutate-test
  (:require [clojure.core.async :as async]
            [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [datomic.api :as d]
            [eponai.common.generators :refer [gen-transaction]]
            [eponai.server.api :as api]
            [eponai.server.routes :as routes]
            [eponai.server.test-util :as util :refer [session-request]]
            [eponai.common.database.pull :refer [pull]]
            [taoensso.timbre :refer [debug]]))

(defn- new-db [txs]
  (let [conn (util/new-db txs)]
    (api/signin conn util/user-email)
    conn))


(defspec
  transaction-created-submitted-to-datomic
  1
  (prop/for-all
    [transaction (gen-transaction)]

    ;; Create new conn with currency transacted.
    (let [conn (new-db [{:db/id         (d/tempid :db.part/user)
                         :currency/code (:currency/code (:transaction/currency transaction))}
                        {:db/id       (d/tempid :db.part/user)
                         :budget/uuid (:budget/uuid (:transaction/budget transaction))}])
          parsed (routes/handle-parser-request
                   (session-request conn `[(transaction/create ~(assoc transaction :mutation-uuid (d/squuid)))]))
          _ (debug "Parsed: " parsed)
          result (get-in parsed ['transaction/create :result])
          db (d/db conn)]

      (are [db-attr input-attr] (pull db '[*] [db-attr (get-in transaction input-attr)])
                                :transaction/uuid [:transaction/uuid]
                                :date/ymd [:transaction/date :date/ymd])
      ;(is (= (async/<!! (get result :currency-chan)) (:input/date transaction)))
      )))