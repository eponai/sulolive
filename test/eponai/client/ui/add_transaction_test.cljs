(ns eponai.client.ui.add_transaction_test
  (:require [eponai.client.ui.add_transaction :as a]
            [eponai.client.ui.transactions :as transactions]
            [datascript.core :as d]
            [eponai.client.parser :as parser]
            [eponai.client.testdata :as testdata]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer-macros [defspec]]
            [om.next :as om]))

(defn gen-amount []
  (gen/fmap str gen/int))

(defn gen-currency []
  (gen/fmap (fn [s] (apply str (take 3 s)))
            (gen/vector gen/char-alpha)))

(defn gen-title []
  gen/string-alphanumeric)

(defn gen-date []
  (gen/fmap (fn [[y m d]] (str y "-" m "-" d))
            (gen/tuple
              (gen/choose 1000 9999)
              (gen/choose 10 12)
              (gen/choose 10 28))))

(defn gen-tags []
  (gen/hash-map :tag/name gen/string-alphanumeric))

(defn gen-transaction []
  (gen/hash-map :input-amount (gen-amount)
                :input-currency (gen-currency)
                :input-title (gen-title)
                :input-date (gen-date)
                :input-description gen/string-alphanumeric
                :input-tags (gen/vector (gen-tags))
                :input-created-at gen/pos-int))

(defspec
  created-transactions-are-rendered
  10
  (prop/for-all
    [transactions (gen/vector (gen-transaction))]
    (let [transactions (map #(assoc % :input-uuid (d/squuid)) transactions)
          create-mutations (map list (repeatedly (fn [] 'transaction/create)) transactions)
          parser (om/parser {:read parser/read :mutate parser/mutate})
          conn (d/create-conn (testdata/datascript-schema))]
      (parser {:state conn} create-mutations)
      (let [ui (parser {:state conn} (om/get-query transactions/AllTransactions))
            txs (set (map :input-uuid transactions))
            rendered-txs (->> (:query/all-dates ui)
                              (mapcat :transaction/_date))]
        (and (= (count txs) (count rendered-txs))
             (every? #(contains? txs (:transaction/uuid %))
                     rendered-txs))))))
