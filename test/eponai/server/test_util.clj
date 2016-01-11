(ns eponai.server.test-util
  (:require [datomic.api :as d]
            [clojure.core.async :as async]
            [eponai.server.auth.credentials :as a]
            [eponai.server.middleware :as m]
            [eponai.common.parser :as parser]))

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

(def user-email "user@email.com")

(def test-parser (parser/parser))

(defn session-request [conn body]
  {:session          {:cemerick.friend/identity
                      {:authentications
                                {1
                                 {:identity 1,
                                  :username user-email,
                                  :roles    #{::a/user}}},
                       :current 1}}
   :body             body
   ::m/parser        test-parser
   ::m/currency-chan (async/chan (async/sliding-buffer 1))
   ::m/conn          conn})