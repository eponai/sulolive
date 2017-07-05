(ns eponai.web.auth0
  (:require
    [taoensso.timbre :refer [debug]]
    [eponai.common.database :as db]
    [eponai.common.shared :as shared]
    [eponai.client.routes :as routes]
    [cemerick.url :as url]
    [eponai.common :as c]))

(defprotocol IAuth0Client
  (login-with-credentials [this email password f])
  (authorize-social [this opts])
  (passwordless-start [this email f])
  (passwordless-verify [this email code f])
  (user-info [this access-token f]))

(defn redirect-to [path]
  (str js/window.location.origin path))

(defmethod shared/shared-component [:shared/auth0 :env/prod]
  [reconciler _ _]
  (let [{:keys [auth0-client-id auth0-domain]} (db/singleton-value (db/to-db reconciler)
                                                                   :ui.singleton.client-env/env-map)
        web-auth (new js/auth0.WebAuth #js {:domain       auth0-domain
                                            :clientID     auth0-client-id
                                            :redirectUri  (redirect-to (routes/url :auth))
                                            :responseType "code"
                                            :scope        "openid email"})]
    (reify IAuth0Client
      (login-with-credentials [_ email password f]
        (.loginWithCredentials (.-redirect web-auth)
                               #js {:connection "Username-Password-Authentication"
                                    :username   email
                                    :password   password}
                               (fn [err res]
                                 (f (js->clj res :keywordize-keys true)
                                    (js->clj err :keywordize-keys true)))))
      (authorize-social [_ {:keys [connection redirectUri]}]
        (let [params (cond-> {:connection connection}
                             (not-empty redirectUri)
                             (assoc :redirectUri redirectUri))]
          (.authorize web-auth (clj->js params))))

      (passwordless-start [this email f]
        (let [params {:connection "email"
                      :send       "code"
                      :email      email}]
          (.passwordlessStart web-auth
                              (clj->js params)
                              (fn [err res]
                                (f (js->clj res :keywordize-keys true)
                                   (js->clj err :keywordize-keys true))))))
      (passwordless-verify [_ email code f]
        (.passwordlessVerify web-auth
                             #js {:connection       "email"
                                  :email            email
                                  :verificationCode code}
                             (fn [err res]
                               (f (js->clj res :keywordize-keys true)
                                  (js->clj err :keywordize-keys true)))))

      (user-info [this access-token f]
        (.userInfo (.-client web-auth)
                   access-token
                   (fn [err user]
                     (f (js->clj user :keywordize-keys true)
                        (js->clj err :keywordize-keys true))))))))

(defmethod shared/shared-component [:shared/auth0 :env/dev]
  [_ _ _]
  (let [auto-login (fn [email]
                     (let [auth-url (-> (url/url (str js/window.location.origin (routes/url :auth)))
                                        (assoc :query {:code email})
                                        (str))]
                       (debug "Replacing the current url with auth-url: " auth-url)
                       (js/window.location.replace auth-url)))]
    (reify IAuth0Client
      (login-with-credentials [_ email _ _]
        (auto-login (or (not-empty email) "dev@sulo.live")))
      (authorize-social [_ _]
        (auto-login "dev@sulo.live"))
      (passwordless-start [_ email _]
        (auto-login email))
      (passwordless-verify [_ _ _ _])
      (user-info [_ access-token f]
        (let [auth-map (c/read-transit (url/url-decode access-token))]
          (f auth-map nil))))))