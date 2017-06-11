(ns eponai.server.external.product-search
  (:require
    [com.stuartsierra.component :as component]
    [eponai.common.search :as common.search]
    [eponai.server.external.datomic :as server.datomic]
    [datomic.api :as datomic]
    [datascript.core :as datascript]
    [eponai.common.database :as db]
    [taoensso.timbre :refer [debug]]
    [clojure.core.async :as async]))

(defn changes-since
  ([db-history-since]
   (changes-since db-history-since :store.item/name))
  ([db-history-or-datoms attr]
   (into []
         (comp
           (map (fn [{:keys [e v added]}]
                  {:db/id e
                   attr   v
                   :added (boolean added)}))
           (common.search/entities-by-attr-tx attr))
         (cond-> db-history-or-datoms
                 (db/database? db-history-or-datoms)
                 (db/datoms :aevt attr)))))

(defn index-item-name-changes-fn [ds-conn]
  (fn [{:keys [db-after db-before]}]
    (let [db-history-since (datomic/since (datomic/history db-after)
                                          (datomic/basis-t db-before))
          item-name-datoms (db/datoms db-history-since :aevt :store.item/name)
          changes (changes-since item-name-datoms :store.item/name)
          retracts? (when (seq changes)
                      (->> item-name-datoms
                           (sequence (comp (filter (comp false? boolean :added))
                                           (take 1)))
                           (seq)
                           (some?)))]
      (when (seq changes)
        (debug "Indexing item name changes: " changes)
        (locking ds-conn
          (common.search/transact ds-conn changes)))
      (when retracts?
        (debug "Garbage collecting product search")
        ;; Doing the garbage collection off this thread, so we can keep going.
        (async/go
          (locking ds-conn
            (swap! ds-conn common.search/gc)))))))

(defrecord ProductSearch [datomic]
  component/Lifecycle
  (start [this]
    (if (:search-conn this)
      this
      (let [datomic-db (db/db (:conn datomic))
            ds-conn (common.search/conn-with-index datomic-db
                                                   :store.item/name)
            listener (server.datomic/add-tx-listener datomic
                                                     (index-item-name-changes-fn ds-conn)
                                                     (fn [error]
                                                       nil))]
        (assoc this :search-conn ds-conn
                    :tx-listener listener))))
  (stop [this]
    (when (:search-conn this)
      (server.datomic/remove-tx-listener datomic (:tx-listener this)))
    (dissoc this :search-conn :tx-listener)))
