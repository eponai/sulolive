(ns eponai.server.auth.workflows
  (:require [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [cemerick.url :as url]
            [eponai.server.auth.facebook :as fb]
            [ring.util.response :as r]
            [ring.util.request :refer [path-info request-url]]))

(defn- redirect-login-failed [& kvs]
  (r/redirect (str "/login.html?fail=Y&" (url/map->query (apply hash-map kvs)))))

(defn form
  []
  (fn [{:keys [params request-method] :as request}]
    ; Verify that we're doing a post request to /login otherwise skip this flow
    (when (and (= :post request-method)
               (= (path-info request)
                  (get-in request [::friend/auth-config :login-uri])))

      (let [{:keys [username password]} params
            credential-fn (get-in request [::friend/auth-config :credential-fn])]

        ; The form should have posted a username and password, if not just skip this flow.
        ; If we have username and password, verify the credentials with the credential-fn.
        (if-let [user-record (and username password
                                  (credential-fn (with-meta params {::friend/workflow :form})))]

          ; Verification successful, make authentication.
          (workflows/make-auth user-record {::friend/workflow          :form
                                            ::friend/redirect-on-auth? true})
          ; Verification failed, redirect back to the login page
          (redirect-login-failed :usr username))))))

(defn facebook
  [app-id app-secret]
  (fn [{:keys [params ::friend/auth-config] :as request}]

    ; fb-login-uri /login/fb will be used if the user tries to login with facebook,
    ; this is just to make it easier to skip any workflows if they're not relevant.
    (let [fb-login-uri (get auth-config :fb-login-uri)
          credential-fn (get auth-config :credential-fn)]

      ; Check if we're in /login/fb otherwise skip this flow
      (when (= (path-info request)
               fb-login-uri)
        (cond
          ; Facebook login succeeded, Facebook will be redirect to /login/fb?code=somecode.
          ; Use the returned code and get/validate access token before authenticating.
          (:code params)
          (let [validated-token (fb/validated-token app-id app-secret (:code params) (request-url request))]
            (if (:error validated-token)
              ; Redirect back to the login page on invalid token, something went wrong.
              (redirect-login-failed :error (:error validated-token))
              ; Successful login, make authentication
              (let [user-record (credential-fn (with-meta validated-token
                                                          {::friend/workflow :facebook}))]
                (workflows/make-auth user-record {::friend/workflow          :facebook
                                                  ::friend/redirect-on-auth? true}))))

          ; User cancelled or denied login, redirect back to the login page.
          (:error params)
          (redirect-login-failed :error (:error params))

          ; Redirect to Facebook login dialog
          true
          (fb/login-dialog app-id (request-url request)))))))
