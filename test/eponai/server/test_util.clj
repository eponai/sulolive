(ns eponai.server.test-util
  (:require [datomic.api :as d]
            [clojure.core.async :as async]
            [eponai.server.auth.credentials :as a]
            [eponai.server.middleware :as m]
            [eponai.common.parser :as parser]
            [eponai.server.datomic-dev :as dev]
            [eponai.common.database.transact :as transact]))

(def schema (dev/read-schema-files))

(defn user-email->user-uuid [db user-email]
  (d/q '{:find  [?uuid .]
         :in    [$ ?email]
         :where [[?u :user/email ?email]
                 [?u :user/uuid ?uuid]]}
       db
       user-email))

(defn new-db
  "Creates an empty database and returns the connection."
  ([]
    (new-db nil))
  ([txs]
   (let [uri "datomic:mem://test-db"]
     (d/delete-database uri)
     (d/create-database uri)
     (let [conn (d/connect uri)]
       (transact/transact conn schema)
       (transact/transact conn [{:db/id         (d/tempid :db.part/user)
                          :currency/code "USD"}])
       (when txs
         (transact/transact conn txs))
       conn))))

(defn setup-db-with-user!
  "Given a users [{:user ... :project-uuid ...} ...], adds:
  * currencies
  * conversion-rates
  * verified user accounts
  * transactions"
  ([users] (setup-db-with-user! (new-db) users))
  ([conn users]
   (dev/add-currencies conn)
   (dev/add-conversion-rates conn)
   (doseq [{:keys [user project-uuid]} users]
     (dev/add-verified-user-account conn user project-uuid)
     (dev/add-transactions conn project-uuid))
    conn))


(def user-email "user@email.com")

(def test-parser (parser/parser))