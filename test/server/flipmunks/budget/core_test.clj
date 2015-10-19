(ns flipmunks.budget.core-test
  (:require [clojure.test :refer :all]
            [datomic.api :only [q db] :as d]
            [flipmunks.budget.core :as b]
            [flipmunks.budget.datomic.pull :as p]
            [flipmunks.budget.datomic.format :as f]
            [flipmunks.budget.datomic.transact :as t]
            [flipmunks.budget.datomic.validate :as v]))

(def schema (read-string (slurp "resources/private/datomic-schema.edn")))

(def test-data [{:transaction/name       "coffee"
                 :transaction/uuid       (str (d/squuid))
                 :transaction/created-at 12345
                 :transaction/date       "2015-10-10"
                 :transaction/amount     100
                 :transaction/currency   "SEK"
                 :transaction/tags       ["fika" "thailand"]}])

(def test-curs {:SEK "Swedish Krona"})

(def test-convs {:date "2015-10-10"
                 :rates {:SEK 8.333}})

(def user {:db/id      (d/tempid :db.part/user)
           :user/email "user@email.com"})

(def request {:session {:cemerick.friend/identity
                        {:authentications
                                  {1
                                   {:identity 1,
                                    :username (user :user/email),
                                    :roles    #{:flipmunks.budget.core/user}}},
                         :current 1}}
              :body test-data})

(defn- new-db
  "Creates an empty database and returns the connection."
  []
  (let [uri "datomic:mem://test-db"]
    (d/delete-database uri)
    (d/create-database uri)
    (let [conn (d/connect uri)]
      (d/transact conn schema)
      (d/transact conn [user])
      conn)))

(defn- db-with-curs []
  (let [conn (new-db)]
    (b/post-currencies conn test-curs)
    conn))

(defn- test-input-db-data
  "Test that the input data mathces the db entities db-data. Checking that count is the same,
  and that all keys in the maps match."
  [input db-data]
  (let [key-set #(set (keys %))]
    (is (= (count input)
           (count db-data)))
    (is (every? true? (map #(= (key-set %1) (key-set %2))
                           input
                           db-data)))))

(deftest test-post-user-data
  (let [db (b/post-user-data (db-with-curs)
                             request
                             test-convs)
        result (b/current-user-data "user@email.com"
                                    (:db-after db)
                                    {})
        no-result (b/current-user-data "invalid@email.com"
                                       (:db-after db)
                                       {})]
    (is (and (empty? (:schema no-result)) (empty? (:entities no-result))))
    (is (= (count (:schema result)) 7))
    (is (= (count (:entities result))
           (+ (count test-data)                              ; Number of transaction entities
              (apply + (map count (map :transaction/tags test-data))) ; Number of tags entities
              (dec (count (filter #(= (:db/valueType %) :db.type/ref)
                                  (:schema result))))))))) ; number of other reference attributes (minus one tags included above)

(deftest test-post-invalid-user-data
  (let [invalid-user-req (assoc-in request
                                   [:session :cemerick.friend/identity :authentications 1 :username]
                                   "invalid@user.com")
        invalid-data-req (assoc request :body [{:invalid-data "invalid"}])
        post-fn #(b/post-user-data (db-with-curs)
                                   %
                                   test-convs)]
    (is (= (:cause (ex-data (post-fn invalid-user-req)))
           ::t/transaction-error))
    (is (= (:cause (ex-data (post-fn invalid-data-req)))
           ::v/validation-error))))

(deftest test-post-invalid-currencies 
  (testing "Posting invalid currency data."
    (let [db (b/post-currencies (new-db)
                                (assoc test-curs :invalid 2))]
      (is (= (:cause (ex-data db)) ::t/transaction-error)))))