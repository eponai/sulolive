(ns eponai.server.datomic-dev
  (:require [datomic.api :as d]
            [environ.core :refer [env]]
            [clojure.tools.reader.edn :as edn]
            [eponai.server.datomic.mocked-data :as mocked]
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

(defn add-data-to-connection
  ([conn add-data? & [schema]]
   (let [schema (or schema (read-schema-files))]
     (db/transact conn schema)
     (debug "Schema added.")
     (when add-data?
       (mocked/add-data conn)
       (debug "Test data added.")))))

(defn create-connection
  [uri {::keys [add-data?]}]
  (try
    (if (contains? #{nil "" "test"} uri)

      (let [_ (info "Setting up inmemory db because uri is set to:" uri)
            mem-conn (create-new-inmemory-db)]
        (add-data-to-connection mem-conn add-data?)
        (debug "Successfully set up inmemory db!")
        mem-conn)
      (do
        (info "Setting up remote db.")
        (d/connect uri)))
    (catch Exception e
      (error "Exception:" e " when trying to connect to datomic=" uri)
      (throw e))))
