(ns eponai.server.core-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [datomic.api :only [q db] :as d]
            [eponai.server.api :as api]
            [eponai.server.core :as c]
            [eponai.server.datomic.pull :as p]
            [eponai.server.datomic.transact :as t]
            [eponai.server.auth.credentials :as a]
            [eponai.server.openexchangerates :as exch]
            [eponai.common.parser :as parser]
            [eponai.server.middleware :as m])
  (:import (clojure.lang ExceptionInfo)))

(def schema (read-string (slurp "resources/private/datomic-schema.edn")))

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
(def test-curs {:SEK "Swedish Krona"})

(def test-parser (parser/parser))

(defn test-convs [date-str]
  {:date  date-str
   :rates {:SEK 8.333}})

(def user-params {:username "user@email.com"
                  :password "password"})

(def request {:session {:cemerick.friend/identity
                        {:authentications
                                  {1
                                   {:identity 1,
                                    :username (user-params :username),
                                    :roles    #{::a/user}}},
                         :current 1}}
              :body test-data
              ::m/parser (parser/parser)
              ::m/currency-chan (async/chan (async/sliding-buffer 1))})

(def mutation-request (assoc request :body `[(transaction/create ~(first test-data))]))

(defn- new-db
  "Creates an empty database and returns the connection."
  []
  (let [uri "datomic:mem://test-db"]
    (d/delete-database uri)
    (d/create-database uri)
    (let [conn (d/connect uri)]
      (d/transact conn schema)
      (api/signup conn user-params)
      conn)))

(defn- db-with-curs []
  (let [conn (new-db)]
    (api/post-currencies conn test-curs)
    conn))

(deftest test-post-currency-rates
  (testing "posting currency rates to database."
    (let [conn (db-with-curs)
          date "2010-10-10"
          db (api/post-currency-rates conn
                                    test-convs
                                    date)
          unposted (api/post-currency-rates conn
                                          test-convs
                                          date)
          converted (p/converted-dates (:db-after db) [date])]
      (is db)
      (is (nil? unposted))
      (is (= (count converted) 1))
      (is (thrown? ExceptionInfo (api/post-currency-rates conn test-convs "invalid-date"))))))

(deftest test-all-data-with-conversions
  (let [conn (db-with-curs)
        {{currency-chan :currency-chan} 'transaction/create} (api/handle-parser-request (assoc mutation-request ::m/conn conn))
        db-conv (api/post-currency-rates conn
                                       test-convs
                                         (:transaction/date (first test-data)))
        result (p/all-data
                 (:db-after db-conv)
                 (user-params :username) {})]
    (is (= (count result) 8))
    (is (some? (async/poll! currency-chan)))))

(deftest test-handle-parser-request
  (let [conn (db-with-curs)
        _ (api/handle-parser-request (assoc mutation-request ::m/conn conn))
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
      (api/handle-parser-request (assoc mutation-request ::m/conn conn))
      (d/transact conn [[:db/add (:db/id user) :user/budget (:db/id budget)]])
      (let [result (p/all-data (d/db conn) "user@email.com" {})]
        (is (= (count result) 7))))))

(defn throw-om-next-error [ret]
  (let [error (get-in ret ['transaction/create :om.next/error])]
    (is (some? error))
    (throw error)))

(deftest test-post-invalid-user-data
  (let [invalid-user-req (assoc-in mutation-request
                                   [:session :cemerick.friend/identity :authentications 1 :username]
                                   "invalid@user.com")
        invalid-data-req (assoc request :body [{:invalid-data "invalid"}])
        post-fn #(api/handle-parser-request (assoc % ::m/conn (db-with-curs)))]
    (is (thrown-with-msg? ExceptionInfo #"Validation failed"
                          (throw-om-next-error (post-fn invalid-user-req))))
    (is (thrown? ExceptionInfo (throw-om-next-error (post-fn invalid-data-req))))))

(deftest test-post-invalid-currencies 
  (testing "Posting invalid currency data."
    (is (thrown? ExceptionInfo (api/post-currencies (new-db)
                                                  (assoc test-curs :invalid 2))))))

(deftest test-signup-unverified
  (testing "user signed up bot not verified"
    (let [valid-params {:username "test"
                        :password "p"}
          conn (new-db)
          email-chan (api/signup conn valid-params)]
      (is (thrown-with-msg? ExceptionInfo
                            #"Email verification pending."
                            ((a/credential-fn conn) valid-params)))
      (is (some? (async/poll! email-chan))))))

(deftest test-signup
  (testing "signup new user"
    (let [valid-params {:username "test"
                        :password "p"}
          conn (new-db)]
      (api/signup conn valid-params)
      (t/new-verification conn
                          (p/user (d/db conn) "test")
                          :user/email
                          :verification.status/verified)
      (is ((a/credential-fn conn) valid-params))
      (is (thrown-with-msg? ExceptionInfo
                            #"Validation failed, "
                            (api/signup (new-db) {:username ""
                                                  :password ""}))))))
