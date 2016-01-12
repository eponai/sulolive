(ns eponai.client.ui.add_transaction_test
  (:require [eponai.client.ui.all_transactions :as transactions]
            [datascript.core :as d]
            [eponai.common.parser :as parser]
            [eponai.common.generators :refer [gen-transaction]]
            [eponai.client.testdata :as testdata]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.clojure-test :refer-macros [defspec]]
            [cljs.test :refer-macros [deftest is]]
            [om.next :as om]
            [clojure.test.check.properties :as prop]))

(defn init-state []
  {:parser (parser/parser)
   :conn (d/create-conn (testdata/datascript-schema))})

;(defspec
;  created-transactions-are-rendered
;  10
;  (prop/for-all
;    [transactions (gen/vector (gen-transaction))]
;    (let [create-mutations (map list (repeatedly (fn [] 'transaction/create)) transactions)
;          {:keys [parser conn]} (init-state)]
;      (doseq [tx transactions]
;        (d/transact conn [{:currency/code (:input-currency tx)}]))
;      (parser {:state conn} create-mutations)
;      (let [ui (parser {:state conn} (om/get-query transactions/AllTransactions))
;            uuids (set (map :input-uuid transactions))
;            txs-by-uuid (group-by :input-uuid transactions)
;            rendered-txs (->> (:query/all-dates ui)
;                              (mapcat :transaction/_date))]
;        (and (= (count uuids) (count rendered-txs))
;             (every? #(and (contains? uuids (:transaction/uuid %))
;                           (= (count (:transaction/tags %))
;                              (count (-> (:transaction/uuid %)
;                                         txs-by-uuid
;                                         first
;                                         :input-tags))))
;                     rendered-txs))))))

(deftest transaction-create-with-tags-of-the-same-name-throws-exception
  (let [{:keys [parser conn]} (init-state)]
    (is (thrown-with-msg? cljs.core.ExceptionInfo
                          #".*Illegal.*argument.*input-tags.*"
                          (-> (parser {:state conn}
                                   '[(transaction/create
                                       {:input-uuid        (d/squuid)
                                        :input-amount      "0"
                                        :input-currency    ""
                                        :input-title       ""
                                        :input-date        "1000-10-10"
                                        :input-description ""
                                        :input-tags        ["" ""]
                                        :input-created-at  0})])
                              (get-in ['transaction/create :om.next/error])
                              (throw))))))
