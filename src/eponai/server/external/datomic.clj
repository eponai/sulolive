(ns eponai.server.external.datomic
  (:require [com.stuartsierra.component :as component]
            [eponai.server.datomic-dev :as datomic-dev]
            [datomic.api :as datomic]
            [taoensso.timbre :refer [debug]]))

(defrecord Datomic [db-url provided-conn]
  component/Lifecycle
  (start [this]
    (if (:conn this)
      this
      (let [conn (or provided-conn (datomic-dev/create-connection db-url))]
        (assoc this :conn conn))))
  (stop [this]
    (when-let [conn (:conn this)]
      (datomic/release conn))
    (dissoc this :conn)))