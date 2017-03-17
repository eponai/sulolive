(ns eponai.server.external.datomic
  (:require [com.stuartsierra.component :as component]
            [eponai.server.datomic-dev :as datomic-dev]
            [datomic.api :as datomic]
            [taoensso.timbre :refer [debug]]))

(defrecord Datomic [db-url provided-conn]
  component/Lifecycle
  (start [this]
    (let [conn (or provided-conn (datomic-dev/create-connection db-url))]
      (assoc this :conn conn)))
  (stop [this]
    (datomic/release (:conn this))
    (dissoc this :conn)))