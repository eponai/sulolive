(ns eponai.server.datomic_mem
  (:require [eponai.server.datomic.transact :as transact]
            [clojure.tools.reader.edn :as edn]
            [clojure.java.io :as io]
            [datomic.api :as d]
            [cemerick.friend.credentials :as creds]
            [eponai.server.datomic.pull :as p])
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

(defn create-new-inmemory-db []
  (let [uri "datomic:mem://test-db"]
    (if (d/create-database uri)
      (d/connect uri)
      (throw (Exception. (str "Could not create datomic db with uri: " uri))))))

(defn add-data-to-connection [conn]
  (let [schema (->> (schema-file) slurp (edn/read-string {:readers *data-readers*}))
        username "test-user@email.com"]
    (d/transact conn schema)
    (println "Schema added.")
    (transact/new-user conn {:username username
                             :bcrypt   (creds/hash-bcrypt "password")})
    (println "New user created")
    (transact/currencies conn currencies)
    (println "Currencies added.")
    (let [{:keys [budget/uuid]} (p/budget (d/db conn) username)]
      (transact/user-txs conn (mapv #(assoc % :transaction/budget uuid) transactions)))
    (println "User transactions added.")
    (let [user (p/user (d/db conn) username)
          verification (->> (p/verifications (d/db conn) user :user/email)
                            first
                            :db/id)]
      (transact/add conn verification :verification/status :verification.status/verified)
      (println "User verified."))))
