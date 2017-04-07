(ns eponai.client.auth
  (:require
    [taoensso.timbre :refer [debug]]
    [eponai.common.database :as db]
    #?(:cljs
       [goog.crypt :as crypt])
    [eponai.common.format.date :as date]))

(defprotocol IAuthLock
  (show-lock [this]))

(defn is-expired-token? [token]
  (let [{:strs [exp]} token]
    (when exp
      (let [today (date/date->long (date/today))]
        (> (* 1000 exp) today)))))


(defn current-auth [db]
  (let [auth (db/lookup-entity db [:ui/singleton :ui.singleton/auth])]
    (debug "Found auth: " auth)
    (get-in auth [:ui.singleton.auth/user :db/id]))
  ;(when-let [token (.getItem js/localStorage "idToken")]
  ;  (let [decoded (crypt/base64.decodeString (second (clojure.string/split token #"\.")))]
  ;    (debug "Decoded: " decoded)
  ;    (when-not (is-expired-token? decoded)
  ;      decoded)))
  )

(defn has-active-user? [db]
  (some? (current-auth db)))
