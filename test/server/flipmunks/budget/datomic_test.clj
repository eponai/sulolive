(ns flipmunks.budget.datomic-test
  (:require [clojure.test :refer :all]
            [datomic.api :only [q db] :as d]
            [flipmunks.budget.datomic.format :as f]
            [flipmunks.budget.datomic.core :as dc]))

(def schema (read-string (slurp "resources/private/datomic-schema.edn")))

(defn empty-db
  "Creates an empty database and returns the connection."
  []
  (let [uri "datomic:mem://test-db"]
    (d/create-database uri)
    (let [conn (d/connect uri)]
      (d/transact conn schema)
      conn)))

(defn speculate [tx]
  (:db-after
    (d/with (d/db (empty-db)) tx)))

(deftest curs-added-and-pulled
  (testing "Adding currencies and pulling, asserting getting same number of txs back."
    (let [test-currencies {:SEK "Swedish Krona"
                           :USD "US Dollar"
                           :THB "Thai Baht"
                           :GBP "British Pound"}]
      (is (= (count test-currencies)
             (-> test-currencies
                 f/curs->db-txs
                 speculate
                 dc/pull-currencies
                 count))))))

(deftest txs-added-and-pulled
  (testing "Adding transactions and pulling, asserting getting same number of txs back."
    (let [test-txs {:name "coffee"
                    :uuid "some id"}])))