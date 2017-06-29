(ns eponai.web.auth0
  (:require
    [taoensso.timbre :refer [debug]]
    [eponai.common.database :as db]
    [eponai.common.shared :as shared]
    [eponai.client.routes :as routes]))

(defprotocol IAuth0Client
  (authorize-social [this connection])
  (passwordless-start [this email f])
  (passwordless-verify [this email code])
  (user-info [this access-token f]))

;(extend-type js/Auth0Lock
;  auth/IAuthLock
;  (show-lock [this]
;    (.show this (clj->js {:allowedConnections ["Username-Password-Authentication"]
;                          :auth               {:params {:state (redirect-to-after-login)
;                                                        :scope "openid email profile"}}}))))

;(defn auth0-lock []
;  (new js/Auth0Lock
;       "JMqCBngHgOcSYBwlVCG2htrKxQFldzDh"
;       "sulo.auth0.com"
;       (clj->js {:auth               {:redirectUrl (str js/window.location.origin (routes/url :auth))}
;                 :languageDictionary {:title "SULO"}
;                 :theme              {:primaryColor        "#9FCFC8"
;                                      :logo                "/assets/img/auth0-icon.png"
;                                      :labeledSubmitButton false}
;                 :allowForgotPassword false})))

(defn redirect-to [path]
  (str js/window.location.origin path))

(defn auth0 [client-id domain]
  (let [web-auth (new js/auth0.WebAuth #js {:domain       domain
                                            :clientID     client-id
                                            :redirectUri  (redirect-to (routes/url :auth))
                                            :responseType "code"
                                            :scope        "openid profile email"})]
    (reify IAuth0Client
      (authorize-social [_ connection]
        (.authorize web-auth #js {:connection connection}))

      (passwordless-start [this email f]
        (let [params {:connection "email" :send "code" :email email}]
          (.passwordlessStart web-auth
                              (clj->js params)
                              (fn [err res]
                                (let [error-code (when err (.-code err))
                                      error (when error-code {:code error-code})]
                                  (f (js->clj res :keywordize-keys true)
                                     (js->clj err :keywordize-keys true))
                                  ;(om/update-state! this (fn [st]
                                  ;                         (cond-> (assoc st :input-email email)
                                  ;                                 (some? error-message)
                                  ;                                 (assoc :error-message error-message)
                                  ;                                 (nil? error-message)
                                  ;                                 (assoc :login-state :verify-email))))
                                  ;(om/update-state! this assoc :input-email email :login-state :verify-email)
                                  )
                                ;(debug "Response: " res)
                                ;(debug "Error: " err)
                                ))))
      (passwordless-verify [_ email code]
        (.passwordlessVerify web-auth
                             #js {:connection       "email"
                                  :email            email
                                  :verificationCode code}
                             (fn [err res]
                               ;(when (and res auth0-manage)
                               ;  (.linkUser auth0-manage (.-user_id res)))
                               (debug "Verified response: " res)
                               (debug "Verified error: " err))))

      (user-info [this access-token f]
        (.userInfo (.-client web-auth)
                   access-token
                   (fn [err user]
                     (f (js->clj user :keywordize-keys true)
                        (js->clj err :keywordize-keys true))
                     ;(cond
                     ;  err
                     ;  (om/update-state! this assoc :token-error {:code (.-code err)})
                     ;  user
                     ;  (om/update-state! this assoc :user {:user_id        (.-user_id user)
                     ;                                      :email          (.-email user)
                     ;                                      :email_verified (boolean (.-email_verified user))
                     ;                                      :nickname       (or (.-given_name user)
                     ;                                                          (.-screen_name user)
                     ;                                                          (.-nickname user))
                     ;                                      :picture        (.-picture_large user)}))
                     (debug "User info: " user)
                     (debug "error " err)))))))

(defmethod shared/shared-component [:shared/auth0 :env/prod]
  [reconciler _ _]
  (let [{:keys [auth0-client-id auth0-domain]} (db/singleton-value (db/to-db reconciler)
                                                                   :ui.singleton.client-env/env-map)]
    (debug "Using stripe-publishable-key: " [auth0-client-id auth0-domain])
    (auth0 auth0-client-id auth0-domain)))

(defmethod shared/shared-component [:shared/auth0 :env/dev]
  [_ _ _]
  (reify IAuth0Client
    (authorize-social [_ _])
    (passwordless-start [_ _ _])
    (passwordless-verify [_ _ _])
    (user-info [_ _ _])))