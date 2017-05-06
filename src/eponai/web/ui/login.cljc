(ns eponai.web.ui.login
  (:require
    [eponai.common.ui.index :as index]
    [eponai.common.ui.router :as router]
    [eponai.client.auth :as auth]
    [om.next :as om :refer [defui]]))


(defui Login
  static om/IQuery
  (query [this]
    [{:proxy/index (om/get-query index/Index)}])
  Object
  (componentDidMount [this]
    (auth/show-lock (:shared/auth-lock (om/shared this))))
  (render [this]
    (index/->Index (:proxy/index (om/props this)))))

(def ->Login (om/factory Login))

(router/register-component :login Login)