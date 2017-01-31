(ns eponai.client.auth
  (:require
    [datascript.core :as d]
    [taoensso.timbre :refer [debug]]
    [eponai.common.database :as db]
    #?(:cljs
       [goog.crypt :as crypt])
    [eponai.common.format.date :as date]))

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

(defn props->user [props]
  (let [{:keys [query/auth]} props]
    (get auth :ui.singleton.auth/user)))

(defn is-logged-in? []
  ;#?(:cljs
  ;   (let [user (logged-in-user)]
  ;     (boolean user)))
  )

(defn has-active-user? [db]
  (throw (ex-info (str "TODO: Either extract as an option to where it's"
                       " being used, or implement this when we need it.")
                  {:todo :implement-function})))

(defn set-logged-in-token [token]
  #?(:cljs
     (.setItem js/localStorage "idToken" token)))