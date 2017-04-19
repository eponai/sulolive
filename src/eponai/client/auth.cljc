(ns eponai.client.auth
  (:require
    [taoensso.timbre :refer [debug]]
    [eponai.client.routes :as routes]
    [eponai.common.database :as db]
    #?(:cljs
       [goog.crypt :as crypt])
    [eponai.common.format.date :as date]
    [cemerick.url :as url]))

(defprotocol IAuthLock
  (show-lock [this]))

(defn current-auth [db]
  (let [auth (db/lookup-entity db [:ui/singleton :ui.singleton/auth])]
    (debug "Found auth: " auth)
    (get-in auth [:ui.singleton.auth/user :db/id])))

(defn authed-email [db]
  (some->> (current-auth db)
           (db/entity db)
           (:user/email)))

(defn has-active-user? [db]
  (some? (current-auth db)))
