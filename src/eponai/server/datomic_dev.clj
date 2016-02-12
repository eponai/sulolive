(ns eponai.server.datomic_dev
  (:require [datomic.api :as d]
            [environ.core :refer [env]]
            [eponai.server.datomic.transact :as server.transact]
            [eponai.common.format :as format]
            [clojure.tools.reader.edn :as edn]
            [eponai.server.datomic.pull :as p]
            [eponai.common.database.pull :as common.pull]
            [clojure.java.io :as io]
            [eponai.common.database.transact :as transact]
            [taoensso.timbre :refer [debug error info]]
            [eponai.server.datomic.format :as f])
  (:import (java.util UUID)))

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

(defn read-schema-file []
  (->> "private/datomic-schema.edn"
       io/resource
       slurp
       (edn/read-string {:readers *data-readers*})))

(defn add-verified-user [conn email]
  (transact/transact-map conn (f/user-account-map email))
  (debug "New user created with email:" email)
  (let [user (p/user (d/db conn) email)
        verification (->> (common.pull/verifications (d/db conn) (:db/id user) :verification.status/pending)
                          first)]
    (server.transact/add conn verification :verification/status :verification.status/verified)))

(defn add-transactions [conn email]
  (let [{:keys [budget/uuid]} (p/budget (d/db conn) email)]
    (->> transactions
         (map #(assoc % :transaction/budget uuid))
         (map format/user-transaction->db-entity)
         ((fn [txs] (debug  "transactions:" txs) txs))
         (transact/transact conn))))

(defn add-currencies [conn]
  (server.transact/currencies conn currencies))

(defn add-conversion-rates [conn]
  (server.transact/currency-rates conn
                                  {:date "2015-10-10"
                                   :rates {:THB 36
                                           :SEK 8.4}}))

(defn add-data-to-connection [conn]
  (let [schema (read-schema-file)
        email test-user-email]
    (d/transact conn schema)
    (debug "Schema added.")
    (add-verified-user conn email)
    (debug "New user created and verified.")
    (add-currencies conn)
    (debug "Currencies added.")
    (add-transactions conn email)
    (debug "User transactions added.")
    (add-conversion-rates conn)
    (debug "Conversion rates added")))

(defonce connection (atom nil))

(defn create-connection [_]
  (let [uri (env :db-url)]
    (try
      (d/connect uri)
      (catch Exception e
        (debug (str "Exception:" e " when trying to connect to datomic=" uri))
        (debug "Will try to set up inmemory db...")
        (try
          (let [mem-conn (create-new-inmemory-db)]
            (add-data-to-connection mem-conn)
            (debug "Successfully set up inmemory db!")
            mem-conn)
          (catch Exception e
            (debug "Exception " e " when trying to set up inmemory db")))))))

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
