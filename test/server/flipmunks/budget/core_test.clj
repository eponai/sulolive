 (ns flipmunks.budget.core-test
   (:require [clojure.test :refer :all]
             [datomic.api :only [q db] :as d]
             [flipmunks.budget.core :as b]
             [flipmunks.budget.datomic.core :as dt]
             [flipmunks.budget.datomic.format :as f]))

(def schema (read-string (slurp "resources/private/datomic-schema.edn")))

(def test-data [{:name       "coffee"
                :uuid       (str (d/squuid))
                :created-at 12345
                :date       "2015-10-10"
                :amount     100
                :currency   "SEK"
                :tags       ["fika" "thailand"]}])

 (def test-curs {:SEK "Swedish Krona"})

(def user {:db/id (d/tempid :db.part/user)
           :user/email "user@email.com"})

 (def session {:cemerick.friend/identity
               {:authentications
                {1
                 {:identity 1,
                  :username (user :user/email),
                  :roles #{:flipmunks.budget.core/user}}},
                :current 1}})

(defn new-db
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

 (defn key-set [m]
   (set (keys m)))

 (defn test-input-db-data
   "Test that the input data mathces the db entities db-data. Checking that count is the same,
   and that all keys in the maps match."
   [input db-data]
   (is (= (count input)
          (count db-data)))
   (is (every? true? (map #(= (key-set %1) (key-set %2))
                          input
                          db-data))))

(deftest test-post-currencies
  (testing "Posting currencies, and verifying pull."
    (let [db (b/post-currencies (new-db)
                                test-curs)
          db-result (b/pull dt/currencies (:db-after db))]
      (test-input-db-data (f/curs->db-txs test-curs) db-result))))

 (deftest test-post-invalid-curs
   (testing "Posting invalid currency data."
     (let [db (b/post-currencies (new-db)
                                 (assoc test-curs :invalid 2))]
       (is (:db/error db)))))

(deftest test-post-transactions
  (testing "Posting user transactions, verify pull."
    (let [db (b/post-user-txs (new-db (f/curs->db-txs test-curs))
                              session
                              test-data)
          pull-fn  #(dt/user-txs (:db-after db) %)]
      (is (:db/error (b/pull pull-fn {})))                  ; Missing parameter :user-id
      (test-input-db-data (f/user-txs->db-txs test-data)
                          (b/pull pull-fn (b/current-user session)))
      (test-input-db-data []
                          (b/pull pull-fn {:username 123}))))) ; User 123 does not exist

(deftest test-post-invalid-user-txs
  (testing "Posting invalid user-txs"
    (let [invalid-data (map #(assoc % :invalid-attr "value")
                            test-data)
          db (b/post-user-txs (new-db (f/curs->db-txs test-curs))
                              session
                              invalid-data)]
      (is (:db/error db)))))

 (deftest test-post-txs-to-invalid-user
   (testing "Posting transactions to non existent user."
     (let [invalid-user (assoc user :username "invalid-email")
           invalid-session (assoc-in session [:cemerick.friend/identity :authentications 1] invalid-user)
           db (b/post-user-txs (new-db (f/curs->db-txs test-curs))
                               invalid-session
                               test-data)]
       (is (:db/error db)))))