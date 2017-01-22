(ns eponai.client.auth
  (:require
    [datascript.core :as d]
    [taoensso.timbre :refer [debug]]
    #?(:cljs
       [goog.crypt :as crypt])
    [eponai.common.format.date :as date]))

(defn is-expired-token? [token]
  (let [{:strs [exp]} token]
    (when exp
      (let [today (date/date->long (date/today))]
        (> (* 1000 exp) today)))))


(defn logged-in-user []
  #?(:cljs
     (when-let [token (.getItem js/localStorage "idToken")]
       (let [decoded (crypt/base64.decodeString (second (clojure.string/split token #"\.")))]
         (debug "Decoded: " decoded)
         (when-not (is-expired-token? decoded)
           decoded)))))

(defn is-logged-in? []
  #?(:cljs
     (let [user (logged-in-user)]
       (boolean user))))

(defn has-active-user? [db]
  (throw (ex-info (str "TODO: Either extract as an option to where it's"
                       " being used, or implement this when we need it.")
                  {:todo :implement-function})))

(defn set-logged-in-token [token]
  #?(:cljs
     (.setItem js/localStorage "idToken" token)))