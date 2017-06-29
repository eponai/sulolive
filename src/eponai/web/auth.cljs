(ns eponai.web.auth
  (:require
    [eponai.client.auth :as auth]
    [eponai.client.routes :as routes]
    [cemerick.url :as url]
    [taoensso.timbre :refer [debug]]
    [eponai.common.shared :as shared]
    [om.next :as om]))

(defprotocol IAuth0
  (passwordless-start [this email]))

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

(defn auth0 []
  )

(defn fake-auth0 []
  )

