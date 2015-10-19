(ns flipmunks.budget.core-test
  (:require [clojure.test :refer :all]
            [datomic.api :only [q db] :as d]
            [flipmunks.budget.core :as b]
            [flipmunks.budget.datomic.pull :as p]
            [flipmunks.budget.datomic.format :as f]
            [flipmunks.budget.datomic.transact :as t]))

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

(def session {:cemerick.friend/identity
              {:authentications
                        {1
                         {:identity 1,
                          :username (user :user/email),
                          :roles    #{:flipmunks.budget.core/user}}},
               :current 1}})

(defn- new-db
  "Creates an empty database and returns the connection."
  ([]
   (new-db nil))
  ([txs]
   (let [uri "datomic:mem://test-db"]
     (d/delete-database uri)
     (d/create-database uri)
     (let [conn (d/connect uri)]
       (d/transact conn schema)
       (d/transact conn [user])
       (when txs
         (d/transact conn txs))
       conn))))

(defn- db-with-curs []
  (new-db (f/curs->db-txs test-curs)))

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
                             {:session session :body test-data}
                             b/test-currency-rates)
        result (b/current-user-data "user@email.com" (:db-after db) {})]
    (is (= (count (:schema result)) 7))
    (is (= (count (:entities result))
           (+ (count test-data)                             ; Number of transaction entities
              (apply + (map count (map :transaction/tags test-data))) ; Number of tags entities
              (dec (count (filter #(= (:db/valueType %) :db.type/ref)
                                  (:schema result))))))))) ; number of other reference attributes (minus one tags included above)

(deftest test-post-currencies
  (testing "Posting currencies, and verifying pull."
    (let [db (b/post-currencies (new-db)
                                test-curs)
          db-result (b/safe p/currencies (:db-after db))]
      (test-input-db-data (f/curs->db-txs test-curs) db-result))))

(deftest test-post-invalid-curs
  (testing "Posting invalid currency data."
    (let [db (b/post-currencies (new-db)
                                (assoc test-curs :invalid 2))]
      (is (ex-data db)))))

(deftest test-post-transactions
  (testing "Posting user transactions, verify pull."
    (let [user-email (:user/email user)
          db (b/post-user-txs (db-with-curs)
                              user-email
                              test-data)
          pull-fn #(p/user-txs (:db-after db) %1 %2)]
      (test-input-db-data [] (b/safe pull-fn nil {}))                  ; Missing parameter :user-id
      (test-input-db-data (f/user-txs->db-txs test-data)
                          (b/safe pull-fn user-email {})) ; valid pull
      (test-input-db-data []
                          (b/safe pull-fn "invalid-email" {}))))) ; User 123 does not exist

(deftest test-post-invalid-user-txs
  (testing "Posting invalid user-txs"
    (let [invalid-data (map #(assoc % :invalid-attr "value")
                            test-data)
          db (b/post-user-txs (db-with-curs)
                              (:user/email user)
                              invalid-data)]
      (is (ex-data db)))))

(deftest test-post-txs-to-invalid-user
  (testing "Posting transactions to non existent user."
    (let [db (b/post-user-txs (db-with-curs)
                              "invalid-email"
                              test-data)]
      (is (ex-data db)))))