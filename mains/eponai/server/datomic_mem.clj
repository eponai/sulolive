(ns eponai.server.datomic_mem
  (:require [eponai.server.core :as core]
            [eponai.server.datomic.transact :as transact]
            [clojure.tools.reader.edn :as edn]
            [clojure.java.io :as io]
            [datomic.api :as d]
            [cemerick.friend.credentials :as creds])
  (:import (java.util UUID)))

(def schema-file (io/file (io/resource "private/datomic-schema.edn")))
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
  (let [schema (->> schema-file slurp (edn/read-string {:readers *data-readers*}))
        username "test-user@email.com"]
    (d/transact conn schema)
    (transact/new-user conn {:username username
                             :bcrypt   (creds/hash-bcrypt "password")})
    (transact/currencies conn currencies)
    (let [{:keys [budget/uuid]} (->> (d/q '[:find ?e :where [?e :budget/uuid]]
                                          (d/db conn))
                                     ffirst
                                     (d/entity (d/db conn)))]
      (transact/user-txs conn (mapv #(assoc % :transaction/budget uuid) transactions)))
    (let [verification (->> (d/q '[:find ?v
                                   :in $ ?username
                                   :where
                                   [?u :user/email ?username]
                                   [?v :verification/entity ?u]]
                                 (d/db conn)
                                 username)
                            ffirst)]
      (transact/add conn verification :verification/status :verification.status/verified))))
