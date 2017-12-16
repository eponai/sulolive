(ns eponai.server.datomic.filter-test
  (:require [clojure.test :as test :refer [deftest]]
            [datomic.api :as datomic]
            [datascript.core :as datascript]
            [eponai.common.database :as db]
            [eponai.server.datomic.filter :as filter]
            [taoensso.timbre :as timbre :refer [debug]]))

(defn create-conn
  ([]
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
                         :store/items]))]
      (create-conn (datascript/create-conn schema))))
  ([conn]
   (let [tempid (fn [& args]
                  (apply db/tempid :db.part/user args))
         photo {:db/id    (tempid -2)
                :photo/id "photo-id"}
         items [{:db/id           (tempid -10)
                 :store.item/name "item 1"}
                {:db/id           (tempid -11)
                 :store.item/name "item 2"}]
         item-refs (into [] (map :db/id) items)
         user-1 {:db/id        (tempid -1)
                 :user/stripe  {:db/id       (tempid)
                                :stripe/publ "user-1 publ"}
                 :user/profile {:db/id              (tempid)
                                :user.profile/name  "Mr. Tester"
                                :user.profile/photo {:db/id (tempid -2)}}
                 :user/cart    {:db/id (tempid)
                                :user.cart/items item-refs}}
         user-2 {:db/id        (tempid -3)
                 :user/stripe  {:db/id       (tempid)
                                :stripe/publ "user-2 publ"}
                 :user/profile {:db/id              (tempid)
                                :user.profile/name  "Dr. Carla"
                                :user.profile/photo {:db/id (tempid -2)}}
                 :user/cart    {:db/id (tempid)
                                :user.cart/items item-refs}}
         store {:db/id (tempid)
                :store/owners {:db/id (tempid)
                               :store.owner/user user-1}
                :store/stripe (:user/stripe user-2)
                :store/items  item-refs}
         order {:order/user  (:db/id user-1)
                :order/store (:db/id store)
                :order/items (map (fn [item]
                                    {:order.item/title (:store.item/name item)
                                     :order.item/amount 100M})
                                  items)}]
     (db/transact conn (concat [photo user-1 user-2 store order]
                               items))
     conn)))

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
      some? store [:store/owners :store.owner/user] #{user-2 user}
      nil? store [:store/owners :store.owner/user] user-2
      some? store [:store/owners :store.owner/user :user/cart :user.cart/items :store/_items] store
      some? user-2 [:user/cart :user.cart/items :store/_items :store/owners :store.owner/user] user
      some? user-2 [:user/cart :user.cart/items :store/_items :store/owners :store.owner/user] #{user-2 user})))

(comment
  (require '[criterium.core :as criterium])
  (require '[eponai.server.datomic-dev :as datomic-dev])

  (let [datomic-conn (datomic-dev/create-new-inmemory-db)
        _ (run! #(db/transact datomic-conn %)
                (datomic-dev/read-schema-files))
        db (db/db (create-conn datomic-conn))
        user (db/one-with db {:where '[[?e :user/profile ?p]
                                       [?p :user.profile/name "Mr. Tester"]]})
        user-2 (db/one-with db {:where '[[?e :user/profile ?p]
                                         [?p :user.profile/name "Dr. Carla"]]})
        store (db/one-with db {:where '[[?e :store/owners]]})
        dbs [[(fn [_ _] false) :no-access]
             [(fn [_ _] true) :all-access]
             [(filter/filter-authed nil #{}) :no-auth]
             [(filter/filter-authed user #{store}) :user+store]
             [(filter/filter-authed user #{}) :user]
             [(filter/filter-authed user-2 #{store}) :user2+store]
             [(filter/filter-authed user-2 #{}) :user2]
             [(filter/filter-authed nil #{store}) :store-only]]
        user-pull [{:order/_user [{:order/items
                                   [:order.item/amount :order.item/title]}]}
                   {:user/cart
                    [:store.item/name
                     {:store/_items
                      [{:store/stripe [:stripe/publ]}
                       {:order/_store [{:order/items
                                        [:order.item/amount :order.item/title]}]}
                       {:store/owners
                        [{:store.owner/user
                          [{:user/profile [:user.profile/name]}]}]}]}]}]
        queries [{:query {:where '[[?e :user/profile ?p]
                                   [?p :user.profile/name "Mr. Tester"]]}
                  :pull  user-pull}
                 {:query {:where '[[?e :user/profile ?p]
                                   [?p :user.profile/name "Dr. Carla"]]}
                  :pull user-pull}
                 {:query {:where '[[?e :store/owners]]}
                  :pull [{:store/items [:store.item/name]}]}]]

    (->> dbs
         (run! (fn [[fn label]]
                 (prn "Benchmarking label: " label)
                 (let [db (datomic/filter db fn)]
                   (criterium/bench
                     (doseq [{:keys [query pull]} queries]
                       (db/pull-one-with db pull query)))))))

    ;; Results on my MacBook Pro 13" early 2015
    ;;
    ;; All times are specified in micro-seconds
    ;; :mean represents execution time mean.
    ;; :lower represents lower quantile ( 2.5%)
    ;; :upper represents upper quantile (97.5%)

    {:no-access     {:mean 338 :lower 334 :upper 347}
     :all-access    {:mean 412 :lower 404 :upper 449}
     :no-auth       {:mean 425 :lower 417 :upper 452}
     :user2         {:mean 436 :lower 426 :upper 469}
     :store-owner   {:mean 509 :lower 501 :upper 534}
     :user          {:mean 518 :lower 508 :upper 571}
     :user+store    {:mean 525 :lower 508 :upper 660}
     :user2+store   {:mean 555 :lower 544 :upper 629}
     }))