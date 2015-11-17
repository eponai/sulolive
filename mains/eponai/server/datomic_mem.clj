(ns eponai.server.datomic_mem
  (:require [eponai.server.core :as core]
            [eponai.server.datomic.pull :as budget.d]
            [clojure.tools.reader.edn :as edn]
            [clojure.java.io :as io]
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

(defn create-new-inmemory-db []
  (let [uri "datomic:mem://test-db"]
    (if (d/create-database uri)
      (d/connect uri)
      (throw (Exception. (str "Could not create datomic db with uri: " uri))))))

(defn add-data-to-connection [conn]
  (let [schema (->> schema-file slurp (edn/read-string {:readers *data-readers*}))]
    (d/transact conn schema)
    (core/post-currencies conn currencies)
    (core/post-user-data {:body transactions} conn transactions)))

