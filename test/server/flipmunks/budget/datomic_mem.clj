(ns flipmunks.budget.datomic_mem
  (:require [flipmunks.budget.core :as core]
            [flipmunks.budget.datomic.core :as budget.d]
            [clojure.tools.reader.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [datomic.api :as d]))

(def schema-file (io/file (io/resource "private/datomic-schema.edn")))
(def currencies [{:THB "Thai Baht"
                  :SEK "Swedish Krona"
                  :USD "US Dollar"}])
(def transactions [{:uuid (str (java.util.UUID/randomUUID))
                     :name "lunch"
                     :date "2015-10-10"
                     :amount 180
                     :currency "THB"}
                    {:uuid (str (java.util.UUID/randomUUID))
                     :name "coffee"
                     :date "2015-10-10"
                     :amount 140
                     :currency "THB"}
                    {:uuid (str (java.util.UUID/randomUUID))
                     :name "dinner"
                     :date "2015-10-10"
                     :amount 350
                     :currency "THB"}
                    {:uuid (str (java.util.UUID/randomUUID))
                     :name "market"
                     :date "2015-10-11"
                     :amount 789
                     :currency "THB"}
                    {:uuid (str (java.util.UUID/randomUUID))
                     :name "lunch"
                     :date "2015-10-11"
                     :amount 125
                     :currency "THB"}])

(def app
  (do 
    ;; set the core/conn var
    (alter-var-root #'core/conn
                    (fn [old-val] 
                      (let [uri "datomic:mem://test-db"]
                        (if (d/create-database uri)
                          (d/connect uri)
                          (throw (Exception. "Could not create datomic db with uri: " uri))))))
    (let [schema (->> schema-file slurp (edn/read-string {:readers *data-readers*}))
          conn core/conn]
      (d/transact conn schema)
      (core/post-currencies currencies)
      (core/post-user-txs transactions))
    ;; reutrn the core/app ring handler
    core/app))

(deftest compiles?
  (is (= app app)))