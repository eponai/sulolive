(ns eponai.client.auth
  (:require
    [taoensso.timbre :refer [debug]]
    [eponai.common.database :as db]
    [om.next :as om]))

(defprotocol ILogin
  (show-login [this]))

(defn login [reconciler-atom]
  (reify ILogin
    (show-login [_]
      (debug "Show login")
      (om/transact! @reconciler-atom [(list 'login-modal/show)
                                      {:query/login-modal [:ui.singleton.loading-bar/show?]}]))))

(defn current-auth [db]
  (some-> (db/lookup-entity db [:ui/singleton :ui.singleton/auth])
          (get-in [:ui.singleton.auth/user :db/id])))


(defn authed-email [db]
  (some->> (current-auth db)
           (db/entity db)
           (:user/email)))


(defn current-locality [x]
  (-> (db/lookup-entity  (db/to-db x) [:ui/singleton :ui.singleton/auth])
      :ui.singleton.auth/locations))
