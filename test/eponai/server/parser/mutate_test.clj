(ns eponai.server.parser.mutate-test
  (:require [clojure.core.async :as async]
            [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [datomic.api :as d]
            [eponai.common.generators :refer [gen-transaction]]
            [eponai.common.parser :as parser]
            [eponai.server.api :as api]
            [eponai.server.site :as site]
            [eponai.server.auth.credentials :as a]
            [eponai.server.middleware :as m]
            [eponai.server.test-util :as util]
            [eponai.common.database.pull :refer [pull]]))

(def test-parser (parser/parser))

(def user-email "user@email.com")

(defn request [conn body]
  {:session {:cemerick.friend/identity
             {:authentications
                       {1
                        {:identity 1,
                         :username user-email,
                         :roles    #{::a/user}}},
              :current 1}}
   :body body
   ::m/parser test-parser
   ::m/currency-chan (async/chan (async/sliding-buffer 1))
   ::m/conn conn})

(defn- new-db [txs]
  (let [conn (util/new-db txs)]
    (api/signin conn user-email)
    conn))

(defspec
  transaction-created-invalid-data-not-submitted
  10
  (let [all-keys #{:input-title
                   :input-created-at
                   :input-uuid
                   :input-date
                   :input-amount
                   :input-currency
                   :input-tags}]
    (prop/for-all
      [transaction (gen-transaction)
       use-keys (gen/set (gen/elements all-keys)
                         {:max-elements (count all-keys)})]

      (let [transaction (select-keys transaction use-keys)
            conn (new-db [{:db/id         (d/tempid :db.part/user)
                           :currency/code (:input-currency transaction)}])
            parsed (site/handle-parser-request (request conn `[(transaction/create ~transaction)]))
            result (get-in parsed ['transaction/create :result])
            error (get-in parsed ['transaction/create :om.next/error])
            db (d/db conn)]
        (if error
          ;; If validation failed transaction were not posted into datomic.
          (do
            (is (= (:cause error) :eponai.common.validate/validation-error))
            (are [db-attr input-attr] (nil? (pull db '[*] [db-attr (get transaction input-attr)]))
                                      :transaction/uuid :input-uuid
                                      :date/ymd :input-date))
          ;; If everything passed, the transaction should be in datomic, together with it's date.
          ;; And currency-chan should have the date value waiting.
          (do
            (are [db-attr input-attr] (pull db '[*] [db-attr (get transaction input-attr)])
                                      :transaction/uuid :input-uuid
                                      :date/ymd :input-date)
            (is (= (async/<!! (get result :currency-chan)) (:input-date transaction)))))))))