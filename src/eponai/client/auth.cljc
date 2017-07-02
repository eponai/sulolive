(ns eponai.client.auth
  (:require
    [taoensso.timbre :refer [debug]]
    [eponai.common.database :as db]
    [om.next :as om]
    [cemerick.url :as url]
    [eponai.client.routes :as routes]))

(defprotocol ILogin
  (show-login [this]))

#?(:cljs
   (def urls-redirecting-to-index
     (into #{} (map routes/url) [:login :landing-page])))

#?(:cljs
   (defn- redirect-to-after-login []
     (let [current-url (str js/window.location.pathname)]
       (if (contains? urls-redirecting-to-index current-url)
         (routes/url :landing-page)
         current-url))))

(defn fake-lock []
  (reify ILogin
    (show-login [this]
      #?(:cljs
         (if-let [email (js/prompt "Enter the email you want to log in as" "dev@sulo.live")]
           (let [auth-url (-> (url/url (str js/window.location.origin (routes/url :auth)))
                              (assoc :query {:code email :state (redirect-to-after-login)})
                              (str))]
             (debug "Replacing the current url with auth-url: " auth-url)
             (js/window.location.replace auth-url))
           (debug "Cancelled log in."))))))

(defn login [reconciler-atom]
  (reify ILogin
    (show-login [_]
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
