(ns eponai.server.parser.read
  (:require
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.parser :as parser :refer [server-read]]
    [eponai.server.datomic.query :as query]
    [taoensso.timbre :as timbre :refer [error debug trace warn]]))

(defmethod server-read :datascript/schema
  [{:keys [db db-history]} _ _]
  {:value (-> (query/schema db db-history)
              (eponai.datascript/schema-datomic->datascript))})

(defmethod server-read :foo
  [{:keys [db db-history]} _ _]
  {:value {:FOO "IS THE SHIT"}})