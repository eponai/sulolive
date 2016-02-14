(ns eponai.server.datomic_dev
  (:require [datomic.api :as d]
            [environ.core :refer [env]]
            [eponai.common.format :as format]
            [clojure.tools.reader.edn :as edn]
            [eponai.server.datomic.pull :as p]
            [eponai.common.database.pull :as common.pull]
            [clojure.java.io :as io]
            [eponai.common.database.transact :as transact]
            [taoensso.timbre :refer [debug error info]]
            [eponai.server.datomic.format :as f])
  (:import (java.util UUID)
           (java.io File)))

(def currencies {:THB "Thai Baht"
                 :SEK "Swedish Krona"
                 :USD "US Dollar"})

(def transactions [{:transaction/uuid       (UUID/randomUUID)
                    :transaction/title       "lunch"
                    :transaction/date       "2015-10-10"
                    :transaction/amount     180
                    :transaction/currency   "THB"
                    :transaction/created-at 1
                    :transaction/tags       ["thailand"]}
                   {:transaction/uuid       (UUID/randomUUID)
                    :transaction/title       "coffee"
                    :transaction/date       "2015-10-10"
                    :transaction/amount     140
                    :transaction/currency   "THB"
                    :transaction/created-at 1}
                   {:transaction/uuid       (UUID/randomUUID)
                    :transaction/title       "dinner"
                    :transaction/date       "2015-10-10"
                    :transaction/amount     350
                    :transaction/currency   "THB"
                    :transaction/created-at 1}
                   {:transaction/uuid       (UUID/randomUUID)
                    :transaction/title       "market"
                    :transaction/date       "2015-10-11"
                    :transaction/amount     789
                    :transaction/currency   "THB"
                    :transaction/created-at 1}
                   {:transaction/uuid       (UUID/randomUUID)
                    :transaction/title       "lunch"
                    :transaction/date       "2015-10-11"
                    :transaction/amount     125
                    :transaction/currency   "THB"
                    :transaction/created-at 1}])

(def test-user-email "test-user@email.com")

(defn create-new-inmemory-db
  ([] (create-new-inmemory-db "test-db"))
  ([db-name]
   (let [uri (str "datomic:mem://" db-name)]
     (d/delete-database uri)
     (d/create-database uri)
     (d/connect uri))))

(defn list-schema-files []
  (let [files (->> "resources/private/datomic/schema/"
                   io/file
                   file-seq
                   (sort-by #(.getName %)))]
    (remove #(.isDirectory %) files)))

(defn read-schema-files
  ([] (read-schema-files (list-schema-files)))
  ([schema-files]
   (let [schemas (map #(->> %
                            slurp
                            (edn/read-string {:readers *data-readers*}))
                      schema-files)]
     (reduce concat [] schemas))))

(defn add-verified-user-account [conn email budget-uuid]
  (let [account (f/user-account-map email {:verification/status :verification.status/verified
                                           :budget/uuid budget-uuid})
        ret (transact/transact-map conn account)]
    (debug "New user created with email:" email)
    ret))

(defn add-transactions [conn budget-uuid]
  (->> transactions
       (map #(assoc % :transaction/budget budget-uuid))
       (map #(format/transaction % {:no-rename true}))
       (transact/transact conn)))

(defn add-currencies [conn]
  (transact/transact conn (f/currencies {:currencies currencies})))

(defn add-conversion-rates [conn]
  (transact/transact conn
                     (f/currency-rates {:date  "2015-10-10"
                                           :rates {:THB 36
                                                   :SEK 8.4}})))

(defn add-data-to-connection [conn]
  (let [schema (read-schema-files)
        email test-user-email
        budget-uuid (d/squuid)]
    (d/transact conn schema)
    (debug "Schema added.")
    (add-currencies conn)
    (debug "Currencies added.")

    (add-verified-user-account conn email budget-uuid)
    (debug "New user created and verified.")
    (add-transactions conn budget-uuid)
    (debug "User transactions added.")
    (add-conversion-rates conn)
    (debug "Conversion rates added")))

(defonce connection (atom nil))

(defn create-connection [_]
  (let [uri (env :db-url)]
    (try
      (if (contains? #{nil "" "test"} uri)
        (let [mem-conn (create-new-inmemory-db)]
          (info "Setting up inmemory db because uri is set to:" uri)
          (add-data-to-connection mem-conn)
          (debug "Successfully set up inmemory db!")
          mem-conn)
        (do
          (info "Setting up remote db.")
          (d/connect uri)))
      (catch Exception e
        (error "Exception:" e " when trying to connect to datomic=" uri)
        (throw e)))))

(defn connect!
  "Returns a connection. Caches the connection when it has successfully connected."
  []
  (debug "Will try to set the database connection...")
  ;; Just set the connection once. Using an atom that's only defined once because,
  ;; there's a ring middleware which (seems to) redefine all vars, unless using defonce.
  (if-let [c @connection]
    (do (debug "Already had a connection. Returning the old one: " c)
        c)
    (swap! connection create-connection)))
