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
            [eponai.server.datomic.format :as f]
            [eponai.common.format :as cf]
            [taoensso.timbre :as timbre]
            [eponai.common.database.pull :as p]))

(defn- new-db [txs]
  (let [conn (util/new-db txs)]
    (api/signin conn util/user-email)
    conn))

(defn session-request [conn user query]
  {:session          {:cemerick.friend/identity
                      {:authentications
                                {1
                                 {:identity 1,
                                  :username (:user/uuid user),
                                  :roles    #{::a/user}}},
                       :current 1}}
   :body             {:query query}
   ::m/parser        (parser/server-parser)
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
                        {:db/id         (d/tempid :db.part/user)
                         :project/uuid  (:project/uuid (:transaction/project transaction))
                         :project/users (:db/id user)}])
          parsed (routes/handle-parser-request
                   (session-request conn user `[(transaction/create ~(assoc transaction ::parser/created-at 1))]))
          _ (debug "Parsed: " parsed)
          result (get-in parsed ['transaction/create :result])
          db (d/db conn)]

      (are [db-attr input-attr] (pull db '[*] [db-attr (get-in transaction input-attr)])
                                :transaction/uuid [:transaction/uuid]
                                :date/ymd [:transaction/date :date/ymd])
      ;(is (= (async/<!! (get result :currency-chan)) (:input/date transaction)))
      )))


(deftest project-deleted-associated-transactions-are-deleted
  (testing "User deletes a project, should delete all related transactions"
    (let [{:keys [user] :as account} (f/user-account-map "test-email")
          project (f/project user)
          transactions (map #(cf/transaction (assoc % :transaction/project (:db/id project))) (eponai.server.datomic-dev/transactions))
          conn (new-db (concat (conj (vals account)
                                     project)
                               transactions))
          pre-delete-transactions (p/all-with (d/db conn) {:where   '[[?p :project/uuid ?uuid]
                                                                    [?e :transaction/project ?p]]
                                                         :symbols {'?uuid (:project/uuid project)}})
          db-project (p/lookup-entity (d/db conn) [:project/uuid (:project/uuid project)])]
      (routes/handle-parser-request
        (session-request conn user `[(project/delete ~{:project-dbid (:db/id db-project) ::parser/created-at 1})]))
      (is (= (count transactions) (count pre-delete-transactions)))
      (is (= (count (p/all-with (d/db conn) {:where '[[?e :transaction/title]]})) 0)))))