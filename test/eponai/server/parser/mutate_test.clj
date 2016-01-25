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
            [eponai.common.database.pull :refer [pull]]))

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
                         :currency/code (:input/currency transaction)}
                        {:db/id       (d/tempid :db.part/user)
                         :budget/uuid (:input/budget transaction)}])
          parsed (routes/handle-parser-request
                   (session-request conn `[(transaction/create ~transaction)]))
          result (get-in parsed ['transaction/create :result])
          db (d/db conn)]

      (are [db-attr input-attr] (pull db '[*] [db-attr (get transaction input-attr)])
                                :transaction/uuid :input/uuid
                                :date/ymd :input/date)
      ;(is (= (async/<!! (get result :currency-chan)) (:input/date transaction)))
      )))