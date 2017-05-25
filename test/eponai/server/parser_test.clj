(ns eponai.server.parser-test
  (:require
    [clojure.test :as test :refer [deftest]]
    [eponai.common.parser :as parser]
    [eponai.server.external.datomic :as datomic]
    [com.stuartsierra.component :as c]
    [eponai.common.database :as db]
    [taoensso.timbre :refer [debug]]))

(deftest parser-filters-database
  (let [datomic (c/start (datomic/->Datomic nil nil true))
        parse (fn [email query]
                (let [parser (parser/server-parser)]
                  (parser {:state (:conn datomic)
                           :auth  {:email email}}
                          query)))

        [store-id owner-email stripe-publ stripe-secret]
        (first (db/find-with (db/db (:conn datomic))
                             {:find  '[?store ?email ?publ ?secret]
                              :where '[[?store :store/profile _]
                                       [?store :store/owners ?owners]
                                       [?owners :store.owner/user ?user]
                                       [?user :user/email ?email]
                                       [?store :store/stripe ?stripe]
                                       [?stripe :stripe/publ ?publ]
                                       [?stripe :stripe/secret ?secret]]}))
        store-query `[({:query/store [{:store/stripe [:stripe/secret
                                                      :stripe/publ]}]}
                        ~{:store-id store-id})]]
    (test/is (number? store-id))
    (test/is (not (empty? (parse nil [:datascript/schema]))))
    (test/is (empty? (get-in (parse nil store-query) [:query/store :store/stripe])))
    (test/is (= (-> (parse owner-email store-query)
                    (get-in [:query/store :store/stripe])
                    (select-keys [:stripe/publ :stripe/secret]))
                {:stripe/publ   stripe-publ
                 :stripe/secret stripe-secret}))
    (c/stop datomic)))
