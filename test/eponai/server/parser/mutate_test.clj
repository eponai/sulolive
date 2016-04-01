(ns eponai.server.parser.mutate-test
  (:require [clojure.core.async :as async]
            [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [datomic.api :as d]
            [eponai.common.generators :refer [gen-transaction]]
            [eponai.server.api :as api]
            [eponai.server.routes :as routes]
            [eponai.server.test-util :as util]
            [eponai.server.middleware :as m]
            [eponai.common.database.pull :refer [pull]]
            [eponai.server.auth.credentials :as a]
            [taoensso.timbre :refer [debug]]
            [eponai.common.parser :as parser]
            [eponai.server.datomic.format :as f]))

(defn- new-db [txs]
  (let [conn (util/new-db txs)]
    (api/signin conn util/user-email)
    conn))

(defn session-request [conn user body]
  {:session          {:cemerick.friend/identity
                      {:authentications
                                {1
                                 {:identity 1,
                                  :username (:user/uuid user),
                                  :roles    #{::a/user}}},
                       :current 1}}
   :body             body
   ::m/parser        (parser/parser)
   ::m/currency-chan (async/chan (async/sliding-buffer 1))
   ::m/conn          conn})

(defspec
  transaction-created-submitted-to-datomic
  1
  (prop/for-all
    [transaction (gen-transaction)]

    ;; Create new conn with currency transacted.
    (let [{:keys [user]} (f/user-account-map "user@email.com")
          conn (new-db [user
                        {:db/id         (d/tempid :db.part/user)
                         :currency/code (:currency/code (:transaction/currency transaction))}
                        {:db/id       (d/tempid :db.part/user)
                         :project/uuid (:project/uuid (:transaction/project transaction))
                         :project/users (:db/id user)}])
          parsed (routes/handle-parser-request
                   (session-request conn user`[(transaction/create ~(assoc transaction :mutation-uuid (d/squuid)))]))
          _ (debug "Parsed: " parsed)
          result (get-in parsed ['transaction/create :result])
          db (d/db conn)]

      (are [db-attr input-attr] (pull db '[*] [db-attr (get-in transaction input-attr)])
                                :transaction/uuid [:transaction/uuid]
                                :date/ymd [:transaction/date :date/ymd])
      ;(is (= (async/<!! (get result :currency-chan)) (:input/date transaction)))
      )))