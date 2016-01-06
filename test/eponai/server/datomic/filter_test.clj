(ns eponai.server.datomic.filter-test
  (:require [clojure.test :refer :all]
            [datomic.api :as d]
            [eponai.server.datomic.filter :as f]
            [eponai.server.datomic_dev :as d_dev]
            [eponai.server.test-util :as util]))

(def user "filter-test@e.com")

(defn make-query [attr]
  `[:find [?e] :where [?e ~attr]])

(deftest filters
  (let [conn (util/new-db)
        _ (d_dev/add-verified-user conn user)
        _ (d_dev/add-currencies conn)
        _ (d_dev/add-transactions conn user)
        _ (d_dev/add-conversion-rates conn)
        db (d/db conn)
        user-db (f/user-db db user)]
    (are [exists? attr database] (exists? (d/q (make-query attr) database))
                                 some? :password/credential db
                                 nil?  :password/credential user-db
                                 some? :currency/name user-db
                                 some? :date/year user-db
                                 some? :tag/name user-db
                                 some? :conversion/date user-db
                                 some? :verification/uuid db
                                 nil?  :verification/uuid user-db)))
