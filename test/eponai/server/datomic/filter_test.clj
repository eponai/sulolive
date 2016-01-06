(ns eponai.server.datomic.filter-test
  (:require [clojure.test :refer :all]
            [datomic.api :as d]
            [eponai.server.datomic.filter :as f]
            [eponai.server.datomic_dev :as d_dev]
            [eponai.server.test-util :as util]))

(def user "filter-test@e.com")

(defn make-query
  [query]
  (let [query (if (vector? (first query))
                query
                [query])
        query (map (fn [v] (if (symbol? (first v))
                             v
                             (cons '?e v)))
                   query)]
    {:find '[[?e ...]] :where query}))

(deftest filters
  (let [conn (util/new-db)
        _ (d_dev/add-verified-user conn user)
        _ (d_dev/add-currencies conn)
        _ (d_dev/add-transactions conn user)
        _ (d_dev/add-conversion-rates conn)
        db (d/db conn)
        user-db (f/user-db db user)
        none (fn [user-res db-res]
               (and (empty? user-res)
                    (seq db-res)))]
    (are [compare query] (compare (d/q (make-query query) user-db)
                                      (d/q (make-query query) db))
                             none [:password/credential]
                             = [:currency/name]
                             = [:date/year]
                             = [:tag/name]
                             = [:conversion/date]
                             none [:verification/uuid]
                             = [:db/ident :db/valueType]
                             = [:db.install/attribute]
                             = [:db/valueType :db.type/ref]
                             = [:db/cardinality :db.cardinality/many]
                             = [:db/ident :transaction/date]
                             = [:db/ident :transaction/date]
                             = '[[?e :db/ident :conversion/date]
                                 [?e :db/valueType ?id]
                                 [?id :db/ident :db.type/ref]])))
