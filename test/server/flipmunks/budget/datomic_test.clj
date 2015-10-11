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

(deftest txs-added-and-pulled
  (testing "Adding transactions and pulling, asserting getting same number of txs back."
    (let [test-txs [{:name       "coffee"
                     :uuid       (str (d/squuid))
                     :created-at (c/to-long (t/today))
                     :date       "2015-10-10"
                     :amount     100
                     :currency   "SEK"
                     :tags       ["fika" "thailand"]}]
          db-cur (speculate (empty-db) (f/curs->db-txs {:SEK "Swedish Krona"}))
          db (speculate db-cur (f/user-txs->db-txs test-txs))
          db-result (dc/pull-user-txs db {:y "2015"})
          entites (partial dc/pull-nested-entities db db-result)]
      (is (= (count test-txs)
             (count db-result)))
      (is (= 1
             (count (entites :transaction/date ))))
      (is (= (count (distinct (flatten (map :tags test-txs))))
             (count (entites :transaction/tags))))
      (is (= 1
             (count (entites :transaction/currency))))
      (is (= 5
            (count (dc/pull-all-data db {:y "2015"})))))))