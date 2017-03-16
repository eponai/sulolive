(ns eponai.server.system
  (:require
    [com.stuartsierra.component :as c]
    [datomic.api :as datomic]
    [eponai.server.datomic-dev :as datomic-dev]
    [datomock.core :as dato-mock]
    [taoensso.timbre :refer [debug]]))

(defn system [env in-prod?]
  (c/system-map
    :system/datomic (map->Datomic {:db-url (:db-url env)
                                   :fork?  (not in-prod?)})))

(defrecord Datomic [db-url fork?]
  c/Lifecycle
  (start [this]
    (let [fork (:fork this)
          _ (debug "Has fork connection? " (some? (:fork this)))
          conn (or fork (datomic-dev/create-connection db-url))]
      (cond-> (assoc this :conn conn)
              fork?
              :fork (dato-mock/fork-conn conn))))
  (stop [this]
    (datomic/release (:conn this))
    (dissoc this :conn)))
