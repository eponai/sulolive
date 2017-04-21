(ns eponai.server.api.user
  (:require
    [eponai.common.database :as db]
    [taoensso.timbre :refer [debug]]))

(defn ->order [state o store-id user-id]
  (let [store (db/lookup-entity (db/db state) store-id)]
    (-> o
        (assoc :order/store store :order/user user-id))))