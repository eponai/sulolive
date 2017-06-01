(ns eponai.server.datomic.filter-test
  (:require [clojure.test :as test :refer [deftest]]
            [datomic.api :as datomic]
            [datascript.core :as datascript]
            [eponai.common.database :as db]
            [eponai.server.datomic.filter :as filter]
            [taoensso.timbre :as timbre :refer [debug]]))

(defn create-conn []
  (let [schema
        (as-> (into {}
                    (map (juxt identity (constantly {:db/valueType :db.type/ref})))
                    [:user/profile
                     :user.profile/photo
                     :user/cart
                     :user.cart/items
                     :store/items
                     :store/owners
                     :store.owner/user])
              $schema
              (reduce (fn [m k]
                        (assoc-in m [k :db/cardinality] :db.cardinality/many))
                      $schema
                      [:user.cart/items
                       :store/items]))
        conn (datascript/create-conn schema)
        photo {:db/id    -2
               :photo/id "photo-id"}
        items [{:db/id -10
                :store.item/name   "item 2"}
               {:db/id -11
                :store.item/name   "item 2"}]
        user-1 {:db/id        -1
                :user/stripe  {:stripe/publ "user-1 publ"}
                :user/profile {:user.profile/name  "Mr. Tester"
                               :user.profile/photo {:db/id -2}}
                :user/cart    {:user.cart/items
                               (into [] (comp (take 1)
                                              (map :db/id))
                                     items)}}
        user-2 {:db/id        -3
                :user/stripe  {:stripe/publ "user-2 publ"}
                :user/profile {:user.profile/name  "Dr. Carla"
                               :user.profile/photo {:db/id -2}}}
        store {:store/owners {:store.owner/user user-1}
               :store/items  (into [] (map :db/id) items)}]
    (db/transact conn (concat [photo user-1 user-2 store]
                              items))
    conn))

(deftest test-walking-entity-graphs
  (let [conn (create-conn)
        db (db/db conn)
        user (db/one-with db {:where '[[?e :user/profile ?p]
                                       [?p :user.profile/name "Mr. Tester"]]})
        user-2 (db/one-with db {:where '[[?e :user/profile ?p]
                                         [?p :user.profile/name "Dr. Carla"]]})
        store (db/one-with db {:where '[[?e :store/owners]]})]
    (test/are [test from path to]
      (test (seq (filter/walk-entity-path db from (filter/attr-path path) to)))

      some? store [:store/owners :store.owner/user] user
      nil? store [:store/owners :store.owner/user] user-2
      some? store [:store/owners :store.owner/user
                   :user/cart :user.cart/items
                   :store/_items] store)))
