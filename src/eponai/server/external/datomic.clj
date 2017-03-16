(ns eponai.server.external.datomic
  (:require [com.stuartsierra.component :as component]
            [eponai.server.datomic-dev :as datomic-dev]
            [datomock.core :as dato-mock]
            [datomic.api :as datomic]
            [taoensso.timbre :refer [debug]]))

(defrecord Datomic [db-url fork?]
  component/Lifecycle
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