(ns eponai.web.ui.login-page
  (:require
    [eponai.web.ui.login :as login]
    [eponai.common.ui.router :as router]
    [om.next :as om :refer [defui]]
    [eponai.web.ui.modal :as modal]
    [taoensso.timbre :refer [debug]]
    [eponai.client.routes :as routes]))


(defui LoginPage
  static om/IQuery
  (query [this]
    [{:proxy/login (om/get-query login/Login)}
     :query/auth0-info])
  Object
  (cancel-login [this]
    (routes/set-url! this :landing-page))
  (render [this]
    (let [{:proxy/keys [login]
           :query/keys [auth0-info]} (om/props this)]
      (modal/modal
        {:id             "sulo-login-modal"
         :size           "tiny"
         :on-close       #(.cancel-login this)
         :require-close? true}
        (login/->Login (om/computed login {:auth0-info auth0-info}))))))

(router/register-component :login LoginPage)
