(ns eponai.web.auth
  (:require
    [eponai.client.auth :as auth]
    [eponai.client.routes :as routes]
    [cemerick.url :as url]
    [taoensso.timbre :refer [debug]]))

(extend-type js/Auth0Lock
  auth/IAuthLock
  (show-lock [this]
    (.show this (clj->js {:allowedConnections ["Username-Password-Authentication"]
                          :auth               {:params {:state js/window.location.origin

                                                        :scope "openid email"}}}))))

(defn auth0-lock []
  (new js/Auth0Lock
       "JMqCBngHgOcSYBwlVCG2htrKxQFldzDh"
       "sulo.auth0.com"
       (clj->js {:auth               {:redirectUrl (str js/window.location.origin (routes/url :auth))}
                 :languageDictionary {:title "SULO"}
                 :theme              {:primaryColor        "#39AC97"
                                      :logo                "/assets/img/auth0-icon.png"
                                      :labeledSubmitButton false}})))
(defn fake-lock []
  (reify auth/IAuthLock
    (show-lock [this]
      (if-let [email (js/prompt "Enter the email you want to log in as" "dev@sulo.live")]
        (let [auth-url (-> (url/url (str js/window.location.origin (routes/url :auth)))
                           (assoc :query {:code email :state (routes/url :index)})
                           (str))]
          (debug "Replacing the current url with auth-url: " auth-url)
          (js/window.location.replace auth-url))
        (debug "Cancelled log in.")))))
