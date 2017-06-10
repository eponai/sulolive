(ns eponai.server.external.product-search
  (:require
    [com.stuartsierra.component :as component]
    [eponai.common.search :as search]
    [datomic.api :as datomic]
    [datascript.core :as datascript]
    [eponai.common.database :as db]))

(defrecord ProductSearch [datomic]
  component/Lifecycle
  (start [this]
    (if (:search-conn this)
      this
      (let [datomic-db (db/db (:conn datomic))
            ds-conn (datascript/create-conn search/search-schema)
            _ (search/transact ds-conn
                               (sequence
                                 (comp (map #(db/entity datomic-db %))
                                       (search/entities-by-attr-tx :store.item/name))
                                 (db/all-with datomic-db {:where '[[?e :store.item/name]]})))]
        (assoc this :search-conn ds-conn))))
  (stop [this]
    (cond-> this (some? (:search-conn this)) (assoc :search-conn nil))))