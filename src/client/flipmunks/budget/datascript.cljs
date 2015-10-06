(ns flipmunks.budget.datascript
  (:require [datascript.core :as d]))

(defn datomic-schema->datascript [datomic-schema]
  (-> (reduce (fn [datascript-s {:keys [db/ident db/valueType] :as datomic-s}]
            (assoc datascript-s ident
                   (-> (if (not= valueType :db.type/ref)
                         (dissoc datomic-s :db/valueType)
                         datomic-s)
                       (select-keys (disj (set (keys datomic-s))
                                          :db/id
                                          :db.install/_attribute
                                          :db/ident)))))
          {}
          datomic-schema)
      (assoc :enum {:db/unique :db.unique/identity})))

