(ns eponai.server.auth.workflows
  (:require [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [environ.core :refer [env]]
            [eponai.server.auth.facebook :as fb]
            [ring.util.response :as r]
            [ring.util.request :refer [path-info request-url]]))

(defn default-flow []
  (fn [{:keys [params request-method] :as request}]
    (when (and (= :post request-method)
               (= (path-info request)
                  (get-in request [::friend/auth-config :login-uri])))
      (let [{:keys [username password]} params
            credential-fn (get-in request [::friend/auth-config :credential-fn])]

        (if-let [user-record (and username password
                                  (credential-fn (with-meta params {::friend/workflow :default})))]

          (workflows/make-auth user-record {::friend/workflow          :default
                                            ::friend/redirect-on-auth? true})
          (r/redirect (str "/login.html?failed=Y&usr=" username)))))))

(defn facebook-flow []
  (fn [{:keys [params] :as request}]
    (let [fb-login-uri (get-in request [::friend/auth-config :fb-login-uri])]
      (when (= (path-info request) fb-login-uri)
        (cond
          (:code params)
          (let [credential-fn (get-in request [::friend/auth-config :credential-fn])
                user-record (credential-fn (with-meta params {::friend/workflow :facebook-flow}))]
            (workflows/make-auth user-record {::friend/workflow          :default
                                              ::friend/redirect-on-auth? true}))

          (:error params)
          (r/redirect (str "/login.html?failed=Y&reason=" (:error_reason params)))

          true
          (fb/login-dialog (env :facebook-app-id) (request-url request)))))))

(defn form
  "Form workflow"
  [& data]
  (apply workflows/interactive-form data))

