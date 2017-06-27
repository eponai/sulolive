(ns eponai.server.parser-test
  (:require
    [clojure.test :as test :refer [deftest]]
    [eponai.common.parser :as parser]
    [eponai.common.parser.util :as parser.util]
    [eponai.common.database :as db]
    [datascript.core :as datascript]
    [eponai.server.external.datomic :as datomic]
    [com.stuartsierra.component :as c]

    [taoensso.timbre :refer [debug]]))

(deftest parser-filters-database
  (let [datomic (c/start (datomic/map->Datomic
                           {:add-mocked-data? true}))
        parse (fn self
                ([email query] (self email query false))
                ([email query use-history?]
                 (let [parser (parser/server-parser)]
                   (parser {:state (:conn datomic)
                            :auth  {:email email}
                            ::parser/read-basis-t-graph
                                   (atom
                                     (reify
                                       parser.util/IReadAtBasisT
                                       (set-basis-t [this key basis-t params]
                                         this)
                                       (get-basis-t [this key params]
                                         (when use-history?
                                           0))
                                       (has-basis-t? [this key]
                                         (if use-history?
                                           true
                                           false))))}
                           query))))

        [store-id owner-email stripe-publ stripe-id]
        (first (db/find-with (db/db (:conn datomic))
                             {:find  '[?store ?email ?publ ?id]
                              :where '[[?store :store/profile _]
                                       [?store :store/owners ?owners]
                                       [?owners :store.owner/user ?user]
                                       [?user :user/email ?email]
                                       [?store :store/stripe ?stripe]
                                       [?stripe :stripe/publ ?publ]
                                       [?stripe :stripe/id ?id]]}))
        store-query `[({:read/with-state
                        [{:query/store [{:store/stripe [:stripe/id :stripe/publ]}
                                        {:stream/_store [:stream/token]}
                                        {:store/owners [{:store.owner/user [{:user/profile [:user.profile/name]}]}]}]}]}
                        {:route-params ~{:store-id store-id}})]
        datascript-schema (:datascript/schema (parse nil [:datascript/schema]))
        ds-db (db/db (datascript/create-conn datascript-schema))]
    (db/transact (:conn datomic)
                 [{:stream/store store-id
                   :stream/token "stream-token"}])

    (test/is (number? store-id))
    (test/is (some? stripe-publ))
    ;; Can get schema (db.part/db datoms).
    (test/is (not (empty? datascript-schema)))

    ;; Test for both db-history and not.
    (->> (for [user [nil owner-email]
               db-history? [false true]]
           [user db-history?])
         (map (fn [[user db-history?]]
                {:user   user
                 :result (-> (parse user store-query db-history?)
                             :read/with-state
                             :query/store
                             (cond-> db-history? (->> (datascript/db-with ds-db))
                                     db-history? (db/entity store-id)))}))
         (run! (fn [{:keys [user result]}]
                 (if (nil? user)
                   ;; Cannot retrieve store/stripe stuff when not authed
                   (do (test/is (empty? (:store/stripe result)))
                       (test/is (nil? (-> result :stream/_store first :stream/token)))
                       (test/is (not (empty? (:store/owners result)))))
                   ;; The owner can get id but not the stripe/publ, since the publ
                   ;; is protected to everyone.
                   (do (test/is (= (-> result
                                       :store/stripe
                                       (select-keys [:stripe/publ :stripe/id]))
                                   ;; The owner can't even get the stripe/publ
                                   ;; but it can get the stripe/id.
                                   {:stripe/id stripe-id}))
                       (test/is (some? (-> result :stream/_store first :stream/token)))
                       (test/is (some? (-> result :store/owners))))))))
    (c/stop datomic)))


(deftest optimize-navigate-gender
  (let [datomic (c/start (datomic/map->Datomic
                           {:add-mocked-data? true}))]
    (try
      (let [server-parse (fn [query]
                           ((parser/server-parser) {:state (:conn datomic)} query))
            datascript-schema (:datascript/schema (server-parse [:datascript/schema]))
            schema (merge-with merge
                               datascript-schema
                               (eponai.common.datascript/ui-schema))
            ds-conn (datascript/create-conn schema)
            client-parse (fn [query]
                           (let [client-parser (parser/client-parser)
                                 env {:state ds-conn}
                                 remote-query (client-parser env query :remote)
                                 server-response (server-parse remote-query)]
                             ;; Poor man's merge
                             (reduce (fn self [conn [k v]]
                                       (if (parser/is-special-key? k)
                                         (reduce self conn v)
                                         (datascript/transact conn v))
                                       conn)
                                     ds-conn
                                     server-response)
                             ((parser/client-parser) {:state ds-conn} query)))]
        (test/is (seq (:query/navigation (client-parse [{:query/navigation [:category/name :category/label :category/path :category/href]}])))))
      (finally
        (c/stop datomic)))))
