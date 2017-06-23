(ns eponai.client.auth
  (:require
    [taoensso.timbre :refer [debug]]
    [eponai.common.database :as db]))

(defprotocol IAuthLock
  (show-lock [this]))

(defn current-auth [db]
  (some-> (db/lookup-entity db [:ui/singleton :ui.singleton/auth])
          (get-in [:ui.singleton.auth/user :db/id])))

(defn authed-email [db]
  (some->> (current-auth db)
           (db/entity db)
           (:user/email)))


(defn current-locality [db]
  (-> (db/lookup-entity  db [:ui/singleton :ui.singleton/auth])
      :ui.singleton.auth/locations))
