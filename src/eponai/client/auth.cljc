(ns eponai.client.auth
  (:require [datascript.core :as d]
            [taoensso.timbre :refer [debug]]))

(defn has-active-user? [db]
  (let [auth (d/entity db [:ui/singleton :ui.singleton/auth])
        current-user (:ui.singleton.auth/user auth)]
    (debug "Remote for current-user status: " current-user)
    (= :user.status/active (get-in current-user [:user/status :db/ident]))))
