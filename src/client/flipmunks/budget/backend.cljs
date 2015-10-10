(ns flipmunks.budget.backend
  (:require [ajax.core :as http]))

(def testdata {2015 {1 {1 {:purchases [{:name "coffee" :cost {:currency "LEK"
                                                              :price 400}}
                                       {:name "dinner" :cost {:currency "LEK"
                                                              :price 1000}}]
                           :rates {"LEK" 0.0081}}
                        2 {:purchases [{:name "lunch" :cost {:currency "LEK"
                                                             :price 600}}]
                           :rates {"LEK" 0.0081}}}}})

(defn success? [req]
  false)

(defn data [req]
  (throw js/Error. "not yet implemented"))

(defn get-dates []
  (prn "TODO: implement the backend so we might get data..?")
  (let [req (http/GET "/the-data")]
    (if (success? req)
      (data req)
      (do 
        (prn "could not get data from the backend. Using test data")
        testdata))))

;; TODO: implement for reals
(defn data-provider []
  (fn [& _]
    {:schema 
[
              ;; Date
              {:db/ident :date/ymd
               :db/unique :db.unique/identity}
              ;; Tag
              {:db/ident :tag/name
               :db/unique :db.unique/identity} 
              ;; Currency
              {:db/ident :currency/name
               :db/unique :db.unique/identity}
              ;; Transactions
              {:db/ident :transaction/date
               :db/valueType :db.type/ref}
              {:db/ident :transaction/tags
               :db/valueType :db.type/ref
               :db/cardinality :db.cardinality/many}
              {:db/ident :transaction/currency
               :db/valueType :db.type/ref}
]

     ;; send entity maps like this?
     ;; the value of a db.type/ref is an id, a number
 :entities [{:date/ymd "2015-01-01" :date/year 2015
             :date/month 01 :date/day 01 :db/id 4711}
            {:date/ymd "2015-01-02" :date/year 2015 
             :date/month 01 :date/day 02  :db/id 4712}
            {:tag/name "lunch" :db/id 1}
            {:tag/name "thailand" :db/id 2}
            {:tag/name "coffee" :db/id 3}
            {:currency/name "TBH" :db/id 300}
            {:transaction/name "lunch"
             :transaction/uuid #uuid "56177857-002d-41b5-99d2-ac527e56fc5c"
             :transaction/date 4711
             :transaction/tags [1 2]
             :transaction/amount 180
             :transaction/currency 300}
            {:transaction/name "coffee"
             :transaction/uuid #uuid "5618a1ce-e3db-4a3e-9953-cb4eb67b42c8"
             :transaction/date 4711
             :transaction/tags [3 2]
             :transaction/amount 140
             :transaction/currency 300}
            {:transaction/name "lunch"
             :transaction/uuid #uuid "5618a1cc-f22f-45d3-954b-2e3b16d10086"
             :transaction/date 4712
             :transaction/tags [1 2]
             :transaction/amount 317
             :transaction/currency 300}]}))


