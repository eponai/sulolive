(ns eponai.server.auth.workflows
  (:require [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [ring.util.response :as r]
            [ring.util.request :refer [path-info]]))

(defn default-flow []
  (fn [{:keys [params request-method] :as request}]
    (when (and (= :post request-method)
               (= (get-in request [::friend/auth-config :login-uri]) (path-info request)))
      (let [{:keys [username password]} params
            credential-fn (get-in request [::friend/auth-config :credential-fn])]

        (if-let [user-record (and username password
                                  (credential-fn (with-meta params {::friend/workflow :default})))]

          (workflows/make-auth user-record {::friend/workflow          :interactive-form
                                            ::friend/redirect-on-auth? true})
          (r/redirect (str "/login.html?failed=true&usr=" username)))))))

(defn form
  "Form workflow"
  [& data]
  (apply workflows/interactive-form data))

