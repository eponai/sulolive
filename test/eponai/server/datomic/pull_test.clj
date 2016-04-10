(ns eponai.server.datomic.pull-test
  (:require [eponai.server.datomic.pull :as s.pull]
            [eponai.common.format :as f]
            [eponai.server.test-util :as util]
            [datomic.api :as d]
            [clojure.test :refer :all]
            [taoensso.timbre :as timbre]))

(deftest pull->query-map-test
         ;; Testing with a datascript db, since it's easier.
         (timbre/with-level
           :trace
           (let [user "other-user@e.com"
                 project-uuid (d/squuid)
                 db (d/db (util/setup-db-with-user! [{:user user :project-uuid project-uuid}]))
                 t-entity (d/entity db (d/q '{:find [?e .] :where [[?e :transaction/uuid]]} db))
                 user-entity (d/entity db (d/q {:find '[?e .] :where [['?e :user/email user]]} db))
                 entity-query {:where [['?e :transaction/uuid (:transaction/uuid t-entity)]]}
                 basis-t (d/basis-t db)]
             (are [txs pull-pattern entity-query res]
               (= (let [db (:db-after (d/with db txs))]
                    (s.pull/pull-all-since db (d/since db basis-t) pull-pattern entity-query))
                  res)
               ;;[] [:db/id] entity-query []
               [] [:transaction/title] entity-query []
               [] [{:transaction/tags [:db/id]}] entity-query []

               ;; Pull tags since
               [{:db/id (:db/id t-entity) :transaction/tags (conj (:transaction/tags t-entity) (f/tag* {:tag/name "foo"}))}]
               [{:transaction/tags [:tag/name]}]
               entity-query
               [{:transaction/tags [{:tag/name "foo"}]}]

               ;; Pull changed user from transaction. This means we'll have to check the pull-pattern
               ;;  to see if anything has changed.
               [{:db/id (:db/id user-entity) :user/currency (f/currency* {:currency/code "XYZ"})}]
               [{:transaction/project [{:project/created-by [{:user/currency [:currency/code]}]}]}]
               entity-query
               [{:transaction/project {:project/created-by {:user/currency {:currency/code "XYZ"}}}}]))))

