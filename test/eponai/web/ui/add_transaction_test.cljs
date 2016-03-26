(ns eponai.web.ui.add-transaction-test
  (:require [eponai.web.ui.all-transactions :as transactions]
            [datascript.core :as d]
            [eponai.common.datascript :as common.datascript]
            [eponai.web.parser.mutate]
            [eponai.web.parser.read]
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
  (let [conn (d/create-conn (merge (testdata/datascript-schema)
                                   (common.datascript/ui-schema)))]
    (d/transact conn [{:ui/singleton :ui.singleton/app}
                      {:ui/singleton :ui.singleton/auth}
                      {:ui/component :ui.component/budget}])
    {:parser (parser/parser)
     :conn   conn}))


(defspec
  created-transactions-are-rendered
  10
  (prop/for-all
    [transactions (gen/vector (gen-transaction))]
    (let [create-mutations (map list (repeatedly (fn [] 'transaction/create)) (map #(assoc % :mutation-uuid (d/squuid))
                                                                                   transactions))
          {:keys [parser conn]} (init-state)]
      (doseq [tx transactions]
        (d/transact conn [(:transaction/currency tx)
                          (:transaction/budget tx)]))
      (let [parsed (parser {:state conn} create-mutations)
            error? (get-in parsed ['transaction/create :om.next/error])
            ui (parser {:state conn} (om/get-query transactions/AllTransactions))
            rendered-txs (:query/transactions ui)]
        (when error?
          (debug "Parsed: " parsed))
        (and
          (not (some? error?))
          (= (count transactions) (count rendered-txs)))))))

(deftest transaction-create-with-tags-of-the-same-name-throws-exception
  (let [{:keys [parser conn]} (init-state)
        budget-uuid (d/squuid)]
    (d/transact conn [{:budget/uuid budget-uuid}])
    (is (thrown-with-msg? cljs.core.ExceptionInfo
                          #".*Illegal.*argument.*input-tags.*"
                          (-> (parser {:state conn}
                                   `[(transaction/create
                                       ~{:transaction/uuid        (d/squuid)
                                        :transaction/amount      "0"
                                        :transaction/currency    {:currency/code ""}
                                        :transaction/title       ""
                                        :transaction/date        {:date/ymd "1000-10-10"}
                                        :transaction/description ""
                                        :transaction/tags        [{:tag/name ""} {:tag/name ""}]
                                        :transaction/created-at  0
                                        :transaction/budget      {:budget/uuid budget-uuid}
                                        :transaction/type        :transaction.type/expense
                                        :mutation-uuid           (d/squuid)})])
                              (get-in ['transaction/create :om.next/error])
                              (throw))))))
