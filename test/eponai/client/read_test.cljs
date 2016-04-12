(ns eponai.client.read-test
  (:require
    [cljs.test :as t :include-macros true]
    [datascript.core :as d]
    [eponai.common.format :as format]
    [eponai.common.parser :as parser]
    [eponai.common.datascript :as common.datascript]
    [eponai.common.testdata :as testdata]
    [eponai.common.database.transact :as transact]
    [taoensso.timbre :refer-macros [debug]]
    [eponai.common.database.pull :as p]))

(defn new-db []
  (let [conn (d/create-conn (merge (testdata/datascript-schema)
                                   (common.datascript/ui-schema)))]
    (transact/transact conn [{:ui/singleton :ui.singleton/app}
                      {:ui/singleton :ui.singleton/auth}
                      {:ui/component :ui.component/project}])
    conn))

(defn conversion [date-ymd currency-code]
  {:db/id               (d/tempid :db.part/user)
   :conversion/date     (format/date* {:date/ymd date-ymd})
   :conversion/currency (format/currency* {:currency/code currency-code})
   :conversion/rate     1.0})

(defn transaction [project date-ymd currency-code]
  (format/transaction
    {:db/id                (d/tempid :db.part/user)
     :transaction/amount   "0"
     :transaction/title      "title"
     :transaction/currency {:currency/code currency-code}
     :transaction/date     {:date/ymd date-ymd}
     :transaction/project   project
     :transaction/type       :transaction.type/expense
     :transaction/uuid       (d/squuid)
     :transaction/created-at 0
     }))

(defn test-user []
  {:db/id (d/tempid :db.part/user)
   :user/uuid (d/squuid)
   :user/email "user@email.com"
   :user/currency {:currency/code "SEK"}})

(defn read-transactions [parser conn user project]
  (parser {:state conn}
         `[({:query/transactions [:db/id {:transaction/date [:date/timestamp :date/ymd]}]} ~{:project-uuid (:project/uuid project)
                                                                                             :conversion-query [:conversion/rate
                                                                                                                {:conversion/date [:date/timestamp]}]})]))

(t/deftest read-transactions-return-conversions-matching-all
  (let [user (test-user)
        project (format/project (:db/id user))
        convs [(conversion "1000-01-01" "SEK")
               (conversion "1000-01-02" "SEK")
               (conversion "1000-01-03" "SEK")
               (conversion "1000-01-01" "USD")
               (conversion "1000-01-02" "USD")
               (conversion "1000-01-03" "USD")]
        conn (new-db)

        ts [(transaction project "1000-01-01" "SEK")
            (transaction project "1000-01-02" "USD")
            (transaction project "1000-01-03" "SEK")]]
    (transact/transact conn (concat [user project
                                     {:ui/singleton :ui.singleton/auth
                                      :ui.singleton.auth/user (:db/id user)}]
                                    convs))
    (transact/transact conn ts)
    (let [{:keys [query/transactions]} (read-transactions (parser/parser) conn user project)]
      (t/is (count ts) (count transactions))
      ;; Conversion rates are attached to the transactions on the client side, vefiry that all the transactions got conversions.
      (t/is (= (count (filter some? (map :transaction/conversion transactions))) (count ts))))))

(t/deftest read-transactions-return-conversions-matching-some
  (let [user (test-user)
        project (format/project (:db/id user))
        convs [(conversion "1000-01-01" "SEK")
               (conversion "1000-01-02" "SEK")
               (conversion "1000-01-03" "SEK")
               (conversion "1000-01-01" "USD")
               (conversion "1000-01-02" "USD")
               (conversion "1000-01-03" "USD")]
        conn (new-db)

        ts [(transaction project "1000-01-01" "SEK")
            (transaction project "1000-01-02" "USD")
            (transaction project "1000-01-04" "SEK")]
        grouped (group-by #(get-in % [:conversion/date :date/timestamp]) convs)]

    (transact/transact conn (concat [user project
                                     {:ui/singleton :ui.singleton/auth
                                      :ui.singleton.auth/user (:db/id user)}]
                                    convs))
    (transact/transact conn ts)
    (let [{:keys [query/transactions]} (read-transactions (parser/parser) conn user project)]
      (t/is (count ts) (count transactions))
      (t/is (= (count (filter some? (map :transaction/conversion transactions))) (count ts)))
      (t/is (every? true? (map (fn [transaction]
                                (let [timestamp (get-in transaction [:transaction/conversion :conversion/date :date/timestamp])
                                      conv (get grouped timestamp)]
                                  (or (some? conv) (= timestamp (get-in (conversion "1000-01-03" "USD") [:conversion/date :date/timestamp])))))
                              transactions))))))