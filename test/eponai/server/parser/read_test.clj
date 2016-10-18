(ns eponai.server.parser.read-test
  (:require
    [clojure.test :refer :all]
    [datomic.api :as d]
    [eponai.common.format :as format]
    [eponai.server.datomic.format :as server.format]
    [eponai.server.test-util :as util]
    [eponai.common.database.pull :as p]
    [eponai.common.database.transact :as transact]
    [taoensso.timbre :as timbre :refer [debug]]))

(defn conversion [date-ymd currency-code]
  {:db/id               (d/tempid :db.part/user)
   :conversion/date     (format/date* date-ymd)
   :conversion/currency (format/currency* {:currency/code currency-code})
   :conversion/rate     (bigdec 1.0)})

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
  (assoc (server.format/user "user@email.com") :user/currency {:currency/code "SEK"}))

(defn read-transactions [conn user project]
  (util/test-parser {:state conn
                     :auth  {:username (:user/uuid user)}} `[({:query/transactions [:db/id]} ~{:project-uuid (:project/uuid project)})]))

(deftest read-transactions-return-conversions-matching-all
  (testing "query/transactions should return transactions and all matching conversions."
    (let [user (test-user)
          project (format/project (:db/id user))
          convs [(conversion "1000-01-01" "SEK")
                 (conversion "1000-01-02" "SEK")
                 (conversion "1000-01-03" "SEK")
                 (conversion "1000-01-01" "USD")
                 (conversion "1000-01-02" "USD")
                 (conversion "1000-01-03" "USD")]
          conn (util/new-db (concat [user
                                     project]
                                    convs))
          ;; Total 4 conversions should be fetched for the following transactions.
          ts [(transaction [:project/uuid (:project/uuid project)] "1000-01-01" "SEK")      ;; One conversions fetched SEK
              (transaction [:project/uuid (:project/uuid project)] "1000-01-02" "USD")      ;; Two conversions fetched SEK+USD (user's + transactions's)
              (transaction [:project/uuid (:project/uuid project)] "1000-01-03" "SEK")]]    ;; One conversions fetched SEK
      (debug "Transactions: " ts)
      (transact/transact conn ts)
      (let [{result :query/transactions} (read-transactions conn user project)
            {:keys [transactions conversions]} result]
        (is (= (count ts) (count transactions)))
        ; We're expecting one more than the number of conversions since one of the transactions is in USD.
        ; That means we're fetching conversions for both USD and SEK (user's currency) for that date
        (is (= (count conversions) 4))))))

(deftest read-transactions-return-conversions-matching-some
  (testing "query/transactions should return transactions and all matching conversions."
    (let [user (test-user)
          project (format/project (:db/id user))
          convs [(conversion "1000-01-01" "SEK")
                 (conversion "1000-01-02" "SEK")
                 (conversion "1000-01-03" "SEK")
                 (conversion "1000-01-01" "USD")
                 (conversion "1000-01-02" "USD")
                 (conversion "1000-01-03" "USD")]
          conn (util/new-db (concat [user
                                     project]
                                    convs))
          ;; Total 3 conversions should be fetched for the following transactions.
          ts [(transaction [:project/uuid (:project/uuid project)] "1000-01-01" "SEK")       ;; One conversions fetched SEK
              (transaction [:project/uuid (:project/uuid project)] "1000-01-02" "USD")       ;; Two conversions fetched SEK+USD (user's + transactions's)
              (transaction [:project/uuid (:project/uuid project)] "1000-01-04" "SEK")]]     ;; Zero conversions fetched SEK (no conversion for date)
      (transact/transact conn ts)
      (let [{result :query/transactions} (read-transactions conn user project)
            {:keys [transactions conversions] :as r} result]
        (prn "Result " r)
        (is (= (count ts) (count transactions)))
        (is (= (count conversions) 3))))))