(ns eponai.client.backend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [ajax.core :as http]
            [cognitect.transit :as t]
            [cljs.core.async :as async]
            [eponai.client.ui.add_transaction :as add_t]))

(def testdata
  {:schema
   [{:db/valueType   :db.type/ref,
     :db/cardinality :db.cardinality/one,
     :db/doc         "Date of the transaction.",
     :db/ident       :transaction/date}
    {:db/unique      :db.unique/identity,
     :db/valueType   :db.type/string,
     :db/cardinality :db.cardinality/one,
     :db/doc         "Three letter currency code, e.g. 'USD'.",
     :db/ident       :currency/code}
    {:db/valueType   :db.type/ref,
     :db/cardinality :db.cardinality/one,
     :db/doc         "Currency of the transaction.",
     :db/ident       :transaction/currency}
    {:db/unique      :db.unique/identity,
     :db/valueType   :db.type/string,
     :db/cardinality :db.cardinality/one,
     :db/doc         "String representation of the date of the form 'yy-MM-dd'.",
     :db/ident       :date/ymd}
    {:db/unique      :db.unique/identity,
     :db/valueType   :db.type/uuid,
     :db/cardinality :db.cardinality/one,
     :db/doc         "Unique identifier for a transaction.",
     :db/ident       :transaction/uuid}
    {:db/unique      :db.unique/identity,
     :db/valueType   :db.type/keyword,
     :db/cardinality :db.cardinality/one,
     :db/doc         "Attribute used to uniquely name an entity.",
     :db/ident       :db/ident}
    {:db/valueType   :db.type/ref,
     :db/cardinality :db.cardinality/many,
     :db/doc         "Attribute used to uniquely name an entity.",
     :db/ident       :transaction/tags}],
   :entities
   [{:transaction/date     {:db/id 17592186045421},
     :transaction/currency {:db/id 17592186045418},
     :transaction/name     "dinner",
     :db/id                17592186045425,
     :transaction/uuid     #uuid "de1ddbd1-883e-4d46-ab89-44da0efd6989",
     :transaction/amount   350}
    {:transaction/date     {:db/id 17592186045421},
     :transaction/currency {:db/id 17592186045418},
     :transaction/name     "lunch",
     :db/id                17592186045420,
     :transaction/uuid     #uuid "bd679ca2-ba83-4366-b8ba-ee7519da2acf",
     :transaction/amount   180}
    {:transaction/date     {:db/id 17592186045421},
     :transaction/currency {:db/id 17592186045418},
     :transaction/name     "coffee",
     :db/id                17592186045423,
     :transaction/uuid     #uuid "75ebb9de-5396-455c-8f4a-0e55d3f65609",
     :transaction/amount   791}
    {:transaction/date     {:db/id 1011},
     :transaction/currency {:db/id 17592186045418},
     :transaction/name     "market",
     :db/id                2001,
     :transaction/uuid     #uuid "75ebb9de-5396-455c-1111-0e55d3f65609",
     :transaction/amount   140}
    {:date/timestamp 1444435200000,
     :date/ymd       "2015-10-10",
     :date/day       10,
     :db/id          17592186045421,
     :date/month     10,
     :date/year      2015}
    {:date/timestamp 1444521600000,
     :date/ymd       "2015-10-11",
     :date/day       11,
     :db/id          1011,
     :date/month     10,
     :date/year      2015}]})

(def test-currencies {:entities [{:currency/name "Thai Baht",
                                  :currency/code "THB",
                                  :db/id         17592186045418}
                                 {:currency/name "Swedish Crown",
                                  :currency/code "SEK",
                                  :db/id         9432000111}]})

(defn GET
  "Put data on the channel. Put the error-data if there's an error (for testing)."
  [chan endpoint error-data]
  (http/GET endpoint
            {:handler         #(let [res %]
                                (prn {:endpoint endpoint :type (type res) :response res})
                                (async/put! chan res))
             :response-format :transit
             ;; Transit handler to read big-int
             :handlers        {"n" cljs.reader/read-string}

             :error-handler   #(async/put! chan error-data)}))

;; TODO: implement for reals
(defn data-provider []
  (fn [c]
    (let [schema-chan (async/chan)
          txs-chan (async/chan)
          curr-chan (async/chan)]
      (go (let [schema (async/<! schema-chan)
                txs (async/<! txs-chan)
                currencies (async/<! curr-chan)]
            (async/>! c {:schema   schema
                         :entities (concat (:entities txs)
                                           (:entities currencies))})))
      ;; Do the actions
      (GET txs-chan "/user/txs" testdata)
      (GET schema-chan "/schema" (:schema testdata))
      (GET curr-chan "/user/curs" test-currencies))))

(defn format-date [d] d)

(defn act-test [data cb]
  (doseq [tx (:remote data)]
    (cond
      (= 'transaction/create (first tx))
      (let [{:keys [::add_t/input-title ::add_t/input-amount ::add_t/input-currency
                    ::add_t/input-date ::add_t/input-tags ::add_t/input-description]
             :as   params} (second tx)
            tx-to-send {:transaction/name     input-title
                        :transaction/currency input-currency
                        :transaction/amount   input-amount
                        :transaction/date     (:date/ymd (add_t/input->date input-date))
                        :transaction/tags     (mapv :tag/name input-tags)

                        ;; not handled on the backend?
                        :transaction/details  input-description}]
        (http/POST "/user/txs"
                   {:body            [tx-to-send]
                    :handler         #(prn "RESPONSE FROM POSTING TRANSACTION: " %)
                    :response-format :transit
                    ;; Transit handler to read big-int
                    :handlers        {"n" cljs.reader/read-string}
                    :error-handler   #(prn "ERROR WHEN POSTING TRANSACTION: " tx-to-send)})
        (prn {:tx (first tx) :params (second tx)}))

      :else (throw (str "unknown send action: " tx)))))
