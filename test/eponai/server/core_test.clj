(ns eponai.server.core-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [datomic.api :only [q db] :as d]
            [eponai.common.parser :as parser]
            [eponai.server.parser.response :as parser.resp]
            [eponai.server.api :as api]
            [eponai.server.core :as c]
            [eponai.server.datomic.pull :as p]
            [eponai.server.datomic.transact :as t]
            [eponai.server.auth.credentials :as a]
            [eponai.server.openexchangerates :as exch]
            [eponai.server.test-util :as util]
            [eponai.server.middleware :as m]
            [eponai.server.site :as site])
  (:import (clojure.lang ExceptionInfo)))

(def test-data [{:transaction/name       "coffee"
                 :transaction/uuid       (d/squuid)
                 :transaction/created-at 12345
                 :transaction/date       "2015-10-10"
                 :transaction/amount     100
                 :transaction/currency   "SEK"
                 :transaction/tags       ["fika" "thailand"]}])

(def test-input [{:input-title "coffee"
                 :input-uuid       (d/squuid)
                 :input-created-at 12345
                 :input-date       "2015-10-10"
                 :input-amount     100
                 :input-currency   "SEK"
                 :input-tags       ["fika" "thailand"]}])
(def test-curs {:SEK "Swedish Krona"
                :THB "Thai Baht"})

(def test-parser (parser/parser))

(defn test-convs [date-str]
  {:date  date-str
   :rates {:SEK 8.333}})

(def user-email "user@email.com")

(def request {:session {:cemerick.friend/identity
                        {:authentications
                                  {1
                                   {:identity 1,
                                    :username user-email,
                                    :roles    #{::a/user}}},
                         :current 1}}
              :body test-data
              ::m/parser test-parser
              ::m/currency-chan (async/chan (async/sliding-buffer 1))})

(def mutation-request (assoc request :body `[(transaction/create ~(first test-data))]))

(defn- new-db []
  (let [conn (util/new-db)]
    (api/signin conn user-email)
    conn))

(defn- db-with-curs []
  (let [conn (new-db)]
    (api/post-currencies conn test-curs)
    conn))

(deftest test-post-currency-rates
  (testing "posting currency rates to database."
    (let [conn (db-with-curs)
          date "2010-10-10"
          db (parser.resp/post-currency-rates conn
                                    util/test-convs
                                    date)
          unposted (parser.resp/post-currency-rates conn
                                          util/test-convs
                                          date)
          converted (p/converted-dates (:db-after db) [date])]
      (is db)
      (is (nil? unposted))
      (is (= (count converted) 1))
      (is (thrown? ExceptionInfo (parser.resp/post-currency-rates conn util/test-convs "invalid-date"))))))

(deftest test-all-data-with-conversions
  (let [conn (db-with-curs)
        parsed (site/handle-parser-request (assoc mutation-request ::m/conn conn))
        db-conv (parser.resp/post-currency-rates conn
                                                 util/test-convs
                                                 (:transaction/date (first test-data)))
        result (p/all-data
                 (:db-after db-conv)
                 user-email {})]
    (is (= (count result) 8))
    (is (some? (async/poll! (get-in parsed ['transaction/create :result :currency-chan]))))))

(deftest test-handle-parser-request
  (let [conn (db-with-curs)
        _ (site/handle-parser-request (assoc mutation-request ::m/conn conn))
        db-after (d/db conn)
        result (p/all-data db-after
                           "user@email.com"
                           {})
        no-result (p/all-data db-after
                              "user@email.com"
                              {:d "1"})]
    (is (empty? no-result))
    (is (= (count result) 7))
    (is (thrown-with-msg? ExceptionInfo
                          #"Invalid budget id"
                          (p/all-data db-after
                                      "invalid@email.com"
                                      {})))))

(deftest test-cyclic-datomic-refs
  (testing "Setup cyclic ref in the schema and test that entity expansion works."
    (let [conn (db-with-curs)
          budget (p/budget (d/db conn) "user@email.com")
          user (p/user (d/db conn) "user@email.com")]
      (d/transact conn [{:db/id                 (d/tempid :db.part/db)
                         :db/ident              :user/budget
                         :db/valueType          :db.type/ref
                         :db/cardinality        :db.cardinality/one
                         :db.install/_attribute :db.part/db}])
      (site/handle-parser-request (assoc mutation-request ::m/conn conn))
      (d/transact conn [[:db/add (:db/id user) :user/budget (:db/id budget)]])
      (let [result (p/all-data (d/db conn) "user@email.com" {})]
        (is (= (count result) 7))))))

(defn om-next-error-msg [ret]
  (get-in ret ['transaction/create :om.next/error ::parser/ex-message]))

(deftest test-post-invalid-user-data
  (let [invalid-user-req (assoc-in mutation-request
                                   [:session :cemerick.friend/identity :authentications 1 :username]
                                   "invalid@user.com")
        invalid-data-req (assoc-in mutation-request
                                   [:body]
                                   `[(transaction/create {:invalid-data "invalid"})])
        post-fn #(site/handle-parser-request (assoc % ::m/conn (db-with-curs)))]
    ;; TODO: Fail faster for invalid user request? #76
    (is (some? (re-find #"Validation failed" (om-next-error-msg (post-fn invalid-user-req)))))
    (is (some? (re-find #"Validation failed" (om-next-error-msg (post-fn invalid-data-req)))))))

(deftest test-post-invalid-currencies 
  (testing "Posting invalid currency data."
    (is (thrown? ExceptionInfo (api/post-currencies (new-db)
                                                  (assoc test-curs :invalid 2))))))

