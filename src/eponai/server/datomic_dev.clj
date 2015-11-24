(ns eponai.server.datomic_dev
  (:require [eponai.server.datomic_mem :as mem]
            [datomic.api :as d]
            [environ.core :refer [env]]))

(defonce connection (atom nil))

(defn create-connection [_]
  (let [uri (env :db-url)]
    (try
      (d/connect uri)
      (catch Exception e
        (prn (str "Exception:" e " when trying to connect to datomic=" uri))
        (prn "Will try to set up inmemory db...")
        (try
          (let [mem-conn (mem/create-new-inmemory-db)]
            (mem/add-data-to-connection mem-conn)
            (prn "Successfully set up inmemory db!")
            mem-conn)
          (catch Exception e
            (prn "Exception " e " when trying to set up inmemory db")))))))

(defn connect [conn]
  (alter-var-root conn
                  (fn [_]
                    (prn "Will try to set the database connection...")
                    ;; Just set the connection once. Using an atom that's only defined once because,
                    ;; there's a ring middleware which (seems to) redefine all vars, unless using defonce.
                    (if-let [c @connection]
                      (do (prn "Already had a connection. Returning the old one: " c)
                          c)
                      (swap! connection create-connection)))))