(ns eponai.server.api.user
  (:require
    [eponai.common.database :as db]
    [taoensso.timbre :refer [debug]]
    [eponai.server.api.store :as store]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.datomic.format :as f]))

(defn ->order [state o store-id user-id]
  (let [store (db/lookup-entity (db/db state) store-id)]
    (-> o
        (assoc :order/store store :order/user user-id))))