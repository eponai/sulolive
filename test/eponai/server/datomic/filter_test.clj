(ns eponai.server.datomic.filter-test
  (:require [clojure.test :refer :all]
            [datomic.api :as d]
            [eponai.common.database.transact :as t]
            [eponai.server.datomic.filter :as f]
            [eponai.server.test-util :as util]
            [taoensso.timbre :as timbre :refer [debug]]))

(defn filter-db [db filters]
  (->> filters
       (f/update-filters db)
       (f/apply-filters db)))

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
  (let [user "filter-test@e.com"
        user2 "other-user@e.com"
        conn (util/new-db)
        budget-uuid (d/squuid)
        budget-uuid2 (d/squuid)
        _ (util/setup-db-with-user! conn [{:user user :budget-uuid budget-uuid}
                                          {:user user2 :budget-uuid budget-uuid2}])
        db (d/db conn)
        user-uuid (util/user-email->user-uuid db user)
        auth-db (filter-db db (f/authenticated-db-filters user-uuid))
        no-auth-db (filter-db db (f/not-authenticated-db-filters))
        something!= (fn [user-res db-res]
                    (and (not-empty user-res)
                         (not-empty db-res)
                         (not= user-res db-res)))
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
                             something!= '[?e :transaction/uuid] auth-db
                             something!= '[?e :budget/uuid] auth-db
                                  ;; something!= '[?e :dashboard/uuid] auth-db
                             none '[?e :transaction/uuid] no-auth-db
                             none '[?e :budget/uuid] no-auth-db
                                  ;; none '[?e :dashboard/uuid] no-auth-db
                             = [:db/ident :db/valueType] auth-db
                             = [:db.install/attribute] auth-db
                             = [:db/valueType :db.type/ref] auth-db
                             = [:db/cardinality :db.cardinality/many] auth-db
                             = [:db/ident :transaction/date] auth-db
                             = [:db/ident :transaction/date] auth-db
                             = '[[?e :db/ident :conversion/date]
                                 [?e :db/valueType ?id]
                                 [?id :db/ident :db.type/ref]] auth-db
                             = [:verification/uuid] auth-db
                             = [:verification/uuid] no-auth-db)))

(deftest filtered-db-contains-newly-created-entity
  (let [user "filter-test@e.com"
        conn (util/new-db)
        budget-uuid (d/squuid)
        _ (util/setup-db-with-user! conn [{:user user :budget-uuid budget-uuid}])
        db (d/db conn)
        user-uuid (util/user-email->user-uuid db user)
        new-budget-uuid (d/squuid)
        auth-filters (f/update-filters db (f/authenticated-db-filters user-uuid))
        {:keys [db-after]} (t/transact conn [{:budget/name       "foo"
                                              :budget/uuid       new-budget-uuid
                                              :budget/created-by [:user/uuid user-uuid]
                                              :budget/users      [[:user/uuid user-uuid]]
                                              :db/id             (d/tempid :db.part/user)}])
        updated-filters (f/update-filters db-after auth-filters)
        db-after-with-filters (f/apply-filters db-after updated-filters)]
    (is (= db-after (d/db conn)))
    (are [db] (some? (d/entity db [:budget/uuid new-budget-uuid]))
              (d/db conn)
              db-after-with-filters)))
