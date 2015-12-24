(ns eponai.server.datomic_dev
  (:require [datomic.api :as d]
            [environ.core :refer [env]]
            [eponai.server.datomic.transact :as transact]
            [clojure.tools.reader.edn :as edn]
            [eponai.server.datomic.pull :as p]
            [cemerick.friend.credentials :as creds]
            [clojure.java.io :as io])
  (:import (java.util UUID)))

(defn schema-file []
  (io/resource "private/datomic-schema.edn"))

(def currencies {:THB "Thai Baht"
                 :SEK "Swedish Krona"
                 :USD "US Dollar"})

(def transactions [{:transaction/uuid       (str (UUID/randomUUID))
                    :transaction/name       "lunch"
                    :transaction/date       "2015-10-10"
                    :transaction/amount     180
                    :transaction/currency   "THB"
                    :transaction/created-at 1}
                   {:transaction/uuid       (str (UUID/randomUUID))
                    :transaction/name       "coffee"
                    :transaction/date       "2015-10-10"
                    :transaction/amount     140
                    :transaction/currency   "THB"
                    :transaction/created-at 1}
                   {:transaction/uuid       (str (UUID/randomUUID))
                    :transaction/name       "dinner"
                    :transaction/date       "2015-10-10"
                    :transaction/amount     350
                    :transaction/currency   "THB"
                    :transaction/created-at 1}
                   {:transaction/uuid       (str (UUID/randomUUID))
                    :transaction/name       "market"
                    :transaction/date       "2015-10-11"
                    :transaction/amount     789
                    :transaction/currency   "THB"
                    :transaction/created-at 1}
                   {:transaction/uuid       (str (UUID/randomUUID))
                    :transaction/name       "lunch"
                    :transaction/date       "2015-10-11"
                    :transaction/amount     125
                    :transaction/currency   "THB"
                    :transaction/created-at 1}])

(def test-user "test-user@email.com")

(defn create-new-inmemory-db []
  (let [uri "datomic:mem://test-db"]
    (d/delete-database uri)
    (d/create-database uri)
    (d/connect uri)))

(defn add-verified-user [conn username]
  (transact/new-user conn {:username username
                           :bcrypt   (creds/hash-bcrypt "password")})
  (println "New user created")
  (let [user (p/user (d/db conn) username)
        verification (->> (p/verifications (d/db conn) user :user/email)
                          first
                          :db/id)]
    (transact/add conn verification :verification/status :verification.status/verified)
    (println "User verified.")))

(defn add-data-to-connection [conn]
  (let [schema (->> (schema-file) slurp (edn/read-string {:readers *data-readers*}))
        username test-user]
    (d/transact conn schema)
    (println "Schema added.")
    (add-verified-user conn username)
    (transact/currencies conn currencies)
    (println "Currencies added.")
    (let [{:keys [budget/uuid]} (p/budget (d/db conn) username)]
      (transact/user-txs conn (mapv #(assoc % :transaction/budget uuid) transactions)))
    (println "User transactions added.")))

(defonce connection (atom nil))

(defn create-connection [_]
  (let [uri (env :db-url)]
    (try
      (d/connect uri)
      (catch Exception e
        (prn (str "Exception:" e " when trying to connect to datomic=" uri))
        (prn "Will try to set up inmemory db...")
        (try
          (let [mem-conn (create-new-inmemory-db)]
            (add-data-to-connection mem-conn)
            (prn "Successfully set up inmemory db!")
            mem-conn)
          (catch Exception e
            (prn "Exception " e " when trying to set up inmemory db")))))))

(defn connect!
  "Returns a connection. Caches the connection when it has successfully connected."
  []
  (prn "Will try to set the database connection...")
  ;; Just set the connection once. Using an atom that's only defined once because,
  ;; there's a ring middleware which (seems to) redefine all vars, unless using defonce.
  (if-let [c @connection]
    (do (prn "Already had a connection. Returning the old one: " c)
        c)
    (swap! connection create-connection)))
