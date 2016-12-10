(ns eponai.server.test-util
  (:require [datomic.api :as d]
            [eponai.common.parser :as parser]
            [eponai.server.datomic-dev :as dev]
            [eponai.common.database :as db]))

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
       (db/transact conn schema)
       (when txs
         (db/transact conn txs))
       conn))))

(defn setup-db-with-user!
  "Given a users [{:user ... :project-uuid ...} ...], adds:
  * currencies
  * conversion-rates
  * verified user accounts
  * transactions"
  [& args]
  (throw (ex-info "This method needs to be re-implemented" {:cause "not sulo enough"})))


(def user-email "user@email.com")

(def test-parser (parser/server-parser))