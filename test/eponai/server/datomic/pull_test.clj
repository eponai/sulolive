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

                 project-1 (f/project (:db/id user-entity) {:project/name "PROJECT-1"})
                 project-2 (f/project (:db/id user-entity) {:project/name "PROJECT-2"})
                 project-2-transaction (assoc t-entity :db/id (d/tempid :db.part/user)
                                                       :transaction/uuid (d/squuid))
                 db (:db-after (d/with db [project-1
                                           (assoc project-2 :db/id (d/tempid :db.part/user -1))
                                           (assoc project-2-transaction :transaction/project (d/tempid :db.part/user -1))]))

                 basis-t (d/basis-t db)]
             (are [txs pull-pattern entity-query res]
               (= (let [db (cond-> db (seq txs) (-> (d/with txs) :db-after))
                        db-since (d/since db basis-t)]
                    (s.pull/pull-all-since db db-since pull-pattern entity-query))
                  res)
               [] [:db/id] transaction-query []
               [] [:transaction/title] transaction-query []
               [] [{:transaction/tags [:db/id]}] transaction-query []

               ;; Change two projects. One which has a pull-pattern entity change and
               ;; another which has a project change. Get both.
               [{:db/id        (d/tempid :db.part/user)
                 :project/uuid (:project/uuid project-1) :project/name "NEW_PROJECT_NAME"}
                {:db/id            (d/tempid :db.part/user)
                 :transaction/uuid (:transaction/uuid project-2-transaction)
                 :transaction/title "NEW_TRANSACTION_NAME"}]
               [:project/name {:transaction/_project [:transaction/title]}]
               {:where   '[[?e :project/uuid ?project-uuid]]
                :symbols {'[?project-uuid ...] [(:project/uuid project-1) (:project/uuid project-2)]}}
               [{:project/name         "NEW_PROJECT_NAME"}
                {:project/name         (:project/name project-2)
                 :transaction/_project [{:transaction/title "NEW_TRANSACTION_NAME"}]}]

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

(deftest query-matching-new-datoms-with-path
  (let [datoms []]
    (let [path [:transaction/tags :tag/name]
          symbols (s.pull/sym-seq path)
          query {:where   [['?e :transaction/tags (first symbols)]
                           [(first symbols) :tag/name (second symbols)]]
                 :symbols {[(first symbols) '...] datoms}}]
      (is (= (s.pull/query-matching-new-datoms-with-path path datoms symbols) query)))

    (let [path [:project/_users :transaction/_project]
          symbols (s.pull/sym-seq path)
          query {:where   [[(first symbols) :project/users '?e]
                           [(second symbols) :transaction/project (first symbols)]]
                 :symbols {[(first symbols) '...] datoms}}]
      (is (= (s.pull/query-matching-new-datoms-with-path path datoms symbols) query)))))
