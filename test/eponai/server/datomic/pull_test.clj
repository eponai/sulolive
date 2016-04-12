(ns eponai.server.datomic.pull-test
  (:require [eponai.server.datomic.pull :as s.pull]
            [eponai.common.database.pull :as c.pull]
            [eponai.common.format :as f]
            [eponai.server.test-util :as util]
            [datomic.api :as d]
            [clojure.test :refer :all]
            [taoensso.timbre :as timbre :refer [debug]]))

(deftest pull->query-map-test
         ;; Testing with a datascript db, since it's easier.
         (timbre/with-level
           :trace
           (let [user "other-user@e.com"
                 project-uuid (d/squuid)
                 db (d/db (util/setup-db-with-user! [{:user user :project-uuid project-uuid}]))
                 t-entity (->> (d/q '{:find [?e .] :where [[?e :transaction/uuid]
                                                           [?e :transaction/tags ?t]]} db)
                               (c.pull/pull db '[{:transaction/tags [*]} :db/id *]))
                 user-entity (d/entity db (d/q {:find '[?e .] :where [['?e :user/email user]]} db))
                 transaction-query {:where [['?e :transaction/uuid (:transaction/uuid t-entity)]]}
                 user-query {:where '[[?e :user/uuid ?user-uuid]]
                             :symbols {'?user-uuid (:user/uuid user-entity)}}
                 basis-t (d/basis-t db)]
             (are [txs pull-pattern entity-query res]
               (= (let [db (cond-> db (seq txs) (-> (d/with txs) :db-after))
                        db-since (d/since db basis-t)]
                    (s.pull/pull-all-since db db-since pull-pattern entity-query))
                  res)
               [] [:db/id] transaction-query []
               [] [:transaction/title] transaction-query []
               [] [{:transaction/tags [:db/id]}] transaction-query []

               ;; Pull tags since
               [{:db/id (:db/id t-entity) :transaction/tags (conj (:transaction/tags t-entity) (f/tag* {:tag/name "foo"}))}]
               [{:transaction/tags [:tag/name]}]
               transaction-query
               [{:transaction/tags (-> t-entity
                                       :transaction/tags
                                       (conj {:tag/name "foo"})
                                       (->> (map #(select-keys % [:tag/name]))))}]

               ;; Pull changed user from transaction. This means we'll have to check the pull-pattern
               ;;  to see if anything has changed.
               [{:db/id (:db/id user-entity) :user/currency (f/currency* {:currency/code "XYZ"})}]
               [:transaction/title
                {:transaction/tags [:tag/name]}
                {:transaction/project [{:project/created-by [{:user/currency [:currency/code]}]}]}]
               transaction-query
               [{:transaction/title   (:transaction/title t-entity)
                 :transaction/tags    (map #(select-keys % [:tag/name]) (:transaction/tags t-entity))
                 :transaction/project {:project/created-by {:user/currency {:currency/code "XYZ"}}}}]

               ;; reverse lookups
               [{:db/id (:db/id t-entity) :transaction/currency (f/currency* {:currency/code "XYZ"})}]
               [{:project/_users [{:transaction/_project [{:transaction/currency [:currency/code]}]}]}]
               user-query
               (assoc-in (c.pull/pull-many db '[{:project/_users [{:transaction/_project [{:transaction/currency [:currency/code]}]}]}]
                                           (c.pull/all-with db user-query))
                         [0 :project/_users 0 :transaction/_project 0 :transaction/currency :currency/code] "XYZ")))))

;; Test this
(comment (deftest path->query
   (are [path query]
     (is (= (s.pull/path->query path '?e) query))
     [:foo] {:where '[[]]})))
