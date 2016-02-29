(ns eponai.server.datomic.pull
  (:require [datomic.api :as d]
            [eponai.common.database.pull :as p]
            [clojure.walk :refer [walk]]))

(defn currencies [db]
  (p/q '[:find [(pull ?e [*]) ...]
       :where [?e :currency/code]] db))

(defn schema
  "Pulls schema from the db. If data is provided includes only the necessary fields for that data.
  (type/ref, cardinality/many or unique/identity)."
  [db]
  (let [schema (p/q '[:find [?e ...]
                      :where
                      [?e :db/ident ?id]
                      [:db.part/db :db.install/attribute ?e]
                      [(namespace ?id) ?ns]
                      [(.startsWith ^String ?ns "db") ?d]
                      [(not ?d)]
                      ] db)]
    (map #(into {} (d/entity db %)) schema)))
