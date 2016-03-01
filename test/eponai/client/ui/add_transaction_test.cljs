(ns eponai.client.ui.add_transaction_test
  (:require [eponai.client.ui.all_transactions :as transactions]
            [datascript.core :as d]
            [eponai.common.datascript :as common.datascript]
            [eponai.common.parser :as parser]
            [eponai.common.generators :refer [gen-transaction]]
            [eponai.client.testdata :as testdata]
            [clojure.test.check]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.clojure-test :refer-macros [defspec]]
            [cljs.test :refer-macros [deftest is]]
            [om.next :as om]
            [taoensso.timbre :refer-macros [debug]]
            [clojure.test.check.properties :as prop :include-macros true]))

(defn init-state []
  {:parser (parser/parser)
   :conn   (d/create-conn (merge (testdata/datascript-schema)
                                 (common.datascript/ui-schema)))})


(defspec
  created-transactions-are-rendered
  10
  (prop/for-all
    [transactions (gen/vector (gen-transaction))]
    (let [create-mutations (map list (repeatedly (fn [] 'transaction/create)) (map #(assoc % :mutation-uuid (d/squuid))
                                                                                   transactions))
          {:keys [parser conn]} (init-state)]
      (doseq [tx transactions]
        (d/transact conn [{:currency/code (:transaction/currency tx)}
                          {:budget/uuid (:transaction/budget tx)}]))
      (let [parsed (parser {:state conn} create-mutations)
            error? (get-in parsed ['transaction/create :om.next/error])
            ui (parser {:state conn} (om/get-query transactions/AllTransactions))
            rendered-txs (:query/all-transactions ui)]
        (when error?
          (debug "Parsed: " parsed))
        (and
          (not (some? error?))
          (= (count transactions) (count rendered-txs)))))))

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
