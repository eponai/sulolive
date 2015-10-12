(ns flipmunks.budget.datomic-test
  (:require [clojure.test :refer :all]
            [datomic.api :only [q db] :as d]
            [flipmunks.budget.datomic.format :as f]
            [flipmunks.budget.datomic.core :as dc]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

(def schema (read-string (slurp "resources/private/datomic-schema.edn")))

(defn empty-db
  "Creates an empty database and returns the connection."
  []
  (let [uri "datomic:mem://test-db"]
    (d/create-database uri)
    (let [conn (d/connect uri)]
      (d/transact conn schema)
      (d/db conn))))

(defn speculate [db txs]
  (:db-after
    (d/with db txs)))

(defn db-with-curs []
  (speculate (empty-db) (f/curs->db-txs {:SEK "Swedish Krona"})))

(deftest curs-added-and-pulled
  (testing "Adding currencies and pulling, asserting getting same number of txs back."
    (let [test-currencies {:SEK "Swedish Krona"
                           :USD "US Dollar"
                           :THB "Thai Baht"
                           :GBP "British Pound"}]
      (is (= (count test-currencies)
             (->> test-currencies
                 f/curs->db-txs
                 (speculate (empty-db))
                 dc/pull-currencies
                 count))))))

(def test-txs [{:name       "coffee"
                :uuid       (str (d/squuid))
                :created-at (c/to-long (t/today))
                :date       "2015-10-10"
                :amount     100
                :currency   "SEK"
                :tags       ["fika" "thailand"]}])

(deftest schema-pulled
  (testing "Pulling schema for given attributes.")
  (let [schema dc/pull-schema]))

(deftest txs-added-and-pulled
  (testing "Adding transactions and pulling, asserting getting same number of txs back."
    (let [db-txs (f/user-txs->db-txs test-txs)
          db (speculate (db-with-curs) db-txs)
          db-result (dc/pull-all-data db {:y "2015"})
          db-schema (dc/pull-schema db db-result)]
      (is (= (dc/distinct-attrs db-result))
          (set (map :db/ident db-schema)))
      (is (every? #(contains? (dc/distinct-attrs db-result) %) (dc/distinct-attrs db-txs)))
      (is (= 5 (count db-result))))))

(deftest txs-added-invalid-attribute
  (testing "Adding transactions with invalid attribute."
    (let [invalid-txs [(assoc (first test-txs) :invalid/attr "value")]])))