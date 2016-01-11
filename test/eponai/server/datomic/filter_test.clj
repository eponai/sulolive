(ns eponai.server.datomic.filter-test
  (:require [clojure.test :refer :all]
            [datomic.api :as d]
            [eponai.server.datomic.filter :as f]
            [eponai.server.datomic_dev :as d_dev]
            [eponai.server.test-util :as util]))

(def user "filter-test@e.com")

(defn make-query
  "Takes queries that binds ?e.
  Can take a query as a single vector or multiple vectors.
  If ?e is not the first vectors first argument, it's added
  into the vector. Examples that work:

  [:attr]
  [?e :attr]
  [[?e :attr]]
  [[:attr][:attr2]]
  [[?e :attr ?y][?y ?attr :value]]"
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
        auth-db (f/authenticated-db db user)
        no-auth-db (f/not-authenticated-db db)
        none (fn [user-res db-res]
               (and (empty? user-res)
                    (seq db-res)))]
    (are [compare query database] (compare (d/q (make-query query) database)
                                      (d/q (make-query query) db))
                             ;none [:password/credential] TODO: remove password entity.
                             = [:currency/name] auth-db
                             = [:date/year] auth-db
                             = [:tag/name] auth-db
                             = [:conversion/date] auth-db
                             none [:verification/uuid] auth-db
                             = [:db/ident :db/valueType] auth-db
                             = [:db.install/attribute] auth-db
                             = [:db/valueType :db.type/ref] auth-db
                             = [:db/cardinality :db.cardinality/many] auth-db
                             = [:db/ident :transaction/date] auth-db
                             = [:db/ident :transaction/date] auth-db
                             = '[[?e :db/ident :conversion/date]
                                 [?e :db/valueType ?id]
                                 [?id :db/ident :db.type/ref]] auth-db
                             none [:verification/uuid] auth-db
                             = [:verification/uuid] no-auth-db)))
