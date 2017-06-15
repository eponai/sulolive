(ns eponai.server.test-util
  (:require
    [clojure.spec :as s]
    [datomic.api :as d]
    [eponai.server.datomic-dev :as dev]
    [eponai.common.database :as db]
    [aleph.netty :as netty]
    [eponai.common.routes :as routes]
    [clj-http.client :as http]
    [taoensso.timbre :refer [debug]]))

(def schemas (dev/read-schema-files))

(s/check-asserts true)

(defn new-db
  "Creates an empty database and returns the connection."
  ([]
   (new-db nil))
  ([txs]
   (let [uri "datomic:mem://test-db"]
     (d/delete-database uri)
     (d/create-database uri)
     (let [conn (d/connect uri)]
       (run! #(db/transact conn %) schemas)
       (when txs
         (db/transact conn txs))
       conn))))

(defn system-db [system]
  (db/db (get-in system [:system/datomic :conn])))

(defn server-url [system]
  (str "http://localhost:" (netty/port (get-in system [:system/aleph :server]))))

(defn endpoint-url [system route & [route-params]]
  (str (server-url system)
       (routes/path route route-params)))

(defn- server-auth-call [system email]
  (http/get (endpoint-url system :auth)
            {:follow-redirects false
             :query-params     {:code  email
                                :state (routes/path :index)}}))

(defmacro with-auth [system email & body]
  `(binding [clj-http.core/*cookie-store* (clj-http.cookies/cookie-store)]
     (#'server-auth-call ~system ~email)
     (do
       ~@body)))

(defn auth-user! [system email cookie-store]
  (binding [clj-http.core/*cookie-store* cookie-store]
    (server-auth-call system email)))


