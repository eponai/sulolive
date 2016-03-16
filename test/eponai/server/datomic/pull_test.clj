(ns eponai.server.datomic.pull-test
  (:require
    [clojure.test :refer :all]
    [clojure.test.check.clojure-test :refer [defspec]]
    [clojure.test.check.properties :as prop]
    [datomic.api :as d]
    [eponai.common.generators :refer [gen-transaction gen-date]]
    [eponai.common.database.pull :refer [pull]]
    [taoensso.timbre :refer [debug]]
    [clojure.test.check.generators :as gen]
    [eponai.common.database.transact :as transact]
    [eponai.server.test-util :as util]
    [eponai.common.format :as f]
    [eponai.common.database.pull :as p]
    [taoensso.timbre :as timbre :refer [trace]]))

(defn gen-conversion [transaction]
  (gen/fmap
    (fn [c]
      {:conversions c
       :transaction transaction})
    (gen/not-empty
      (gen/vector
        (gen/fmap
          (fn [[date amount]]
            {:conversion/date     date
             :conversion/currency (:transaction/currency transaction)
             :conversion/rate     (bigdec amount)})
          (gen/tuple
            (gen-date)
            (gen/double* {:min 0.01 :NaN? false :infinite? false})))))))

(defspec
  test-latest-conversion-is-always-found-if-no-conversion
  20
  (prop/for-all [transaction-conversions (gen/bind (gen-transaction) gen-conversion)]
                (let [{:keys [transaction conversions]} transaction-conversions
                      conn (util/new-db (f/add-tempid (conj conversions (:transaction/budget transaction))))
                      _ (transact/transact-one conn (f/transaction transaction))
                      latest-conversion (p/find-latest-conversion (d/db conn) (:transaction/currency transaction))]
                  
                  (let [latest-date (p/pull (d/db conn) [{:conversion/date [:date/timestamp]}] latest-conversion)
                        conv-by-date (group-by :conversion/date conversions)
                        latest-generated-date (first (sort-by :date/timestamp #(> %1 %2) (keys conv-by-date)))]

                    (is (= (:date/timestamp (:conversion/date latest-date))
                           (:date/timestamp latest-generated-date)))))))