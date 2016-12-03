(ns eponai.web.ui.add-transaction-test
  (:require [eponai.web.ui.project.all-transactions :as transactions]
            [datascript.core :as d]
            [eponai.common.datascript :as common.datascript]
            [eponai.web.parser.mutate]
            [eponai.web.parser.read]
            [eponai.common.parser :as parser]
            [eponai.common.generators :refer [gen-transaction gen-project]]
            [eponai.common.testdata :as testdata]
            [clojure.test.check]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.clojure-test :refer-macros [defspec]]
            [cljs.test :refer-macros [deftest is]]
            [om.next :as om]
            [taoensso.timbre :as timbre :refer-macros [debug error]]
            [clojure.test.check.properties :as prop :include-macros true]))

(defn init-state []
  (let [user-uuid (d/squuid)
        conn (d/create-conn (merge (testdata/datascript-schema)
                                   (common.datascript/ui-schema)))]
    (d/transact conn [{:ui/singleton :ui.singleton/app}
                      {:ui/singleton :ui.singleton/auth}
                      {:ui/component :ui.component/project}
                      {:user/uuid user-uuid :db/id -1}
                      {:ui/singleton           :ui.singleton/auth
                       :ui.singleton.auth/user -1}])
    {:parser (parser/test-client-parser)
     :conn   conn
     :user-uuid user-uuid}))

(defspec
  created-transactions-are-rendered
  10
  (prop/for-all
    [transactions (gen/bind (gen-project) (fn [project] (gen/vector (gen-transaction (gen/return project)))))]
    (try (let [create-mutations (mapv list (repeatedly (fn [] 'transaction/create)) transactions)
               {:keys [parser conn user-uuid]} (init-state)]
           (doseq [tx transactions]
             (d/transact conn [(:transaction/currency tx)
                               (-> (:transaction/project tx)
                                   (assoc :project/users [[:user/uuid user-uuid]]))]))

           (let [parsed (parser {:state conn} create-mutations)
                 error? (get-in parsed ['transaction/create :om.next/error])
                 ui (parser {:state conn} (om/get-query transactions/AllTransactions))
                 rendered-txs (:query/transactions ui)]
             (when error?
               (error "Parsed: " parsed))
             (is (not (some? error?)))
             (is (= (count transactions) (count rendered-txs)))
             true))
         (catch :default e
           (error "test error: " e)
           (throw e)))))

(deftest transaction-create-with-tags-of-the-same-name-throws-exception
  (let [{:keys [parser conn user-uuid]} (init-state)
        project-uuid (d/squuid)]
    (d/transact conn [{:project/uuid project-uuid :project/users [[:user/uuid user-uuid]]}])
    ;; Hide error prints. Set to :error or lower too see errors logged.
    (timbre/with-level
      :fatal
      (is (thrown-with-msg? cljs.core.ExceptionInfo
                            #".*validation error.*transaction/create"
                            (some-> (parser {:state conn}
                                            `[(transaction/create
                                                ~{:transaction/uuid        (d/squuid)
                                                  :transaction/amount      "0"
                                                  :transaction/currency    {:currency/code ""}
                                                  :transaction/title       ""
                                                  :transaction/date        {:date/ymd "1000-10-10"}
                                                  :transaction/description ""
                                                  :transaction/tags        [{:tag/name ""} {:tag/name ""}]
                                                  :transaction/created-at  0
                                                  :transaction/project     {:project/uuid project-uuid}
                                                  :transaction/type        :transaction.type/expense})])
                                    (get-in ['transaction/create :om.next/error])
                                    (throw)))))))
