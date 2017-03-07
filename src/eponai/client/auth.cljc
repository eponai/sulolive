(ns eponai.client.auth
  (:require
    [taoensso.timbre :refer [debug]]
    [eponai.client.routes :as routes]
    [eponai.common.database :as db]
    #?(:cljs
       [goog.crypt :as crypt])
    #?(:cljs [cljs-http.client :as http])
    [eponai.common.format.date :as date]
    [cemerick.url :as url]))

(defprotocol IAuthLock
  (show-lock [this]))

#?(:cljs
   (extend-type js/Auth0Lock
     IAuthLock
     (show-lock [this]
       (.show this (clj->js {:allowedConnections ["Username-Password-Authentication"]
                             :auth               {:params {:state js/window.location.origin

                                                           :scope "openid email"}}})))))

#?(:cljs
   (defn auth0-lock []
     (new js/Auth0Lock
          "JMqCBngHgOcSYBwlVCG2htrKxQFldzDh"
          "sulo.auth0.com"
          (clj->js {:auth               {:redirectUrl (str js/window.location.origin (routes/url :auth))}
                    :languageDictionary {:title "SULO"}
                    :theme              {:primaryColor        "#39AC97"
                                         :logo                "/assets/img/auth0-icon.png"
                                         :labeledSubmitButton false}}))))
#?(:cljs
   (defn fake-lock []
     (reify IAuthLock
       (show-lock [this]
         (if-let [email (js/prompt "Enter the email you want to log in as" "dev@sulo.live")]
           (let [auth-url (-> (url/url (str js/window.location.origin (routes/url :auth)))
                              (assoc :query {:code email :state (routes/url :index)})
                              (str))]
             (debug "Replacing the current url with auth-url: " auth-url)
             (js/window.location.replace auth-url))
           (js/alert (str "Must enter an email to log in. Got email: " email)))))))


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
