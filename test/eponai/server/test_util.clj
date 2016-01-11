(ns eponai.server.test-util
  (:require [datomic.api :as d]))

(def schema (read-string (slurp "resources/private/datomic-schema.edn")))

(defn new-db
  "Creates an empty database and returns the connection."
  ([]
    (new-db nil))
  ([txs]
   (let [uri "datomic:mem://test-db"]
     (d/delete-database uri)
     (d/create-database uri)
     (let [conn (d/connect uri)]
       (d/transact conn schema)
       (when txs
         (d/transact conn txs))
       conn))))

(defn test-convs [date-str]
  {:date  date-str
   :rates {:SEK 8.333
           :THB 231}})