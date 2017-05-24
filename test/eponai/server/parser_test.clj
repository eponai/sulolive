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

        [store-id owner-email stripe-publ :as found]
        (first (db/find-with (db/db (:conn datomic))
                             {:find  '[?store ?email ?publ]
                              :where '[[?store :store/profile _]
                                       [?store :store/owners ?owners]
                                       [?owners :store.owner/user ?user]
                                       [?user :user/email ?email]
                                       [?store :store/stripe ?stripe]
                                       [?stripe :stripe/publ ?publ]]}))
        store-query `[({:query/store [{:store/stripe [:stripe/publ]}]}
                        ~{:store-id store-id})]]
    (debug "Found: " found)
    (test/is (number? store-id))
    (test/is (nil? (get-in (parse nil store-query)
                           [:query/store :store/stripe :stripe/publ])))
    (test/is (= stripe-publ
                (get-in (parse owner-email store-query)
                        [:query/store :store/stripe :stripe/publ])))
    (c/stop datomic)))
