(ns eponai.server.external.product-search
  (:require
    [com.stuartsierra.component :as component]
    [eponai.common.search :as common.search]
    [eponai.server.external.datomic :as server.datomic]
    [datomic.api :as datomic]
    [datascript.core :as datascript]
    [eponai.common.database :as db]
    [taoensso.timbre :refer [debug]]))

(defn changes-since
  ([db-history-since]
   (changes-since db-history-since :store.item/name))
  ([db-history-since attr]
   (into []
         (comp
           (map (fn [{:keys [e v added]}]
                  {:db/id e attr v :added (boolean added)}))
           (common.search/entities-by-attr-tx attr))
         (db/datoms db-history-since :aevt attr))))

(defrecord ProductSearch [datomic]
  component/Lifecycle
  (start [this]
    (if (:search-conn this)
      this
      (let [datomic-db (db/db (:conn datomic))
            ds-conn (datascript/create-conn common.search/search-schema)
            _ (common.search/transact
                ds-conn
                (sequence
                  (comp (map #(db/entity datomic-db %))
                        (common.search/entities-by-attr-tx :store.item/name))
                  (db/all-with datomic-db {:where '[[?e :store.item/name]]})))
            listener (server.datomic/add-tx-listener
                       datomic
                       (fn [{:keys [db-after db-before] :as tx-report}]
                         (let [db-history-since (datomic/since (datomic/history db-after)
                                                               (datomic/basis-t db-before))]
                           (common.search/transact ds-conn (changes-since db-history-since))))
                       (fn [error]
                         nil))]
        (assoc this :search-conn ds-conn
                    :tx-listener listener))))
  (stop [this]
    (when (:search-conn this)
      (server.datomic/remove-tx-listener datomic (:tx-listener this)))
    (dissoc this :search-conn :tx-listener)))
