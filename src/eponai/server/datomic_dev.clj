(ns eponai.server.datomic-dev
  (:require [datomic.api :as d]
            [environ.core :refer [env]]
            [clojure.tools.reader.edn :as edn]
            [clojure.java.io :as io]
            [eponai.common.database :as db]
            [eponai.common.database.functions :as dbfn]
            [taoensso.timbre :refer [debug error info]]))

(defn create-new-inmemory-db
  ([] (create-new-inmemory-db (str (gensym "test-db"))))
  ([db-name]
   (let [uri (str "datomic:mem://" db-name)]
     (d/delete-database uri)
     (d/create-database uri)
     (d/connect uri))))

(defn database-functions-schema []
  {:db/id    #db/id [:db.part/user]
   :db/ident :db.fn/edit-attr
   :db/fn    (dbfn/dbfn dbfn/edit-attr)})

(defn- parse-resource [resource]
  (edn/read-string {:readers *data-readers*} (slurp resource)))

(defn list-schema-files []
  (for [i (range)
        :let [schema (io/resource (str "private/datomic/schema/schema-" i ".edn"))]
        :while schema]
    schema))

(defn read-schema-files
  ([] (read-schema-files (list-schema-files)))
  ([schema-files]
   (let [schema (into [] (mapcat parse-resource) schema-files)]
     (conj schema (database-functions-schema)))))

(defn add-test-data [conn]
  (let [mocked-data (parse-resource (clojure.java.io/resource "private/mocked-data.edn"))
        txs (into [] cat (vals (dissoc mocked-data :cart)))]
    (db/transact conn txs)
    ;; Transact the cart once we have the store and item data.
    (db/transact conn (:cart mocked-data))))

(defn add-data-to-connection
  ([conn & [schema]]
   (let [schema (or schema (read-schema-files))]
     (db/transact conn schema)
     (debug "Schema added.")
     (add-test-data conn)
     (debug "Test data added."))))

(defonce connection (atom nil))

(defn create-connection []
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
    (reset! connection (create-connection))))
