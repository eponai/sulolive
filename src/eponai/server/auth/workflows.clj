(ns eponai.server.auth.workflows
  (:require [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [cemerick.url :as url]
            [clojure.core.async :refer [go]]
            [eponai.server.auth.facebook :as fb]
            [ring.util.response :as r]
            [ring.util.request :refer [path-info request-url]])
  (:import (clojure.lang ExceptionInfo)))

(defn- redirect-login-failed [& kvs]
  (r/redirect (str "/signup?fail=Y&" (url/map->query (apply hash-map kvs)))))

(defn- redirect-create-account [account-info]
  (r/redirect (str "/signup?new=Y&"
                   (url/map->query account-info))))

(defn- redirect-verify-email []
  (r/redirect (str "/signup?verify=Y")))

(defn form
  []
  (fn [{:keys [params ::friend/auth-config] :as request}]
    (let [credential-fn (get auth-config :credential-fn)
            login-uri (get auth-config :email-login-uri)]

      ; Verify that we're doing a post request to /api/login/email otherwise skip this flow
      (when (and (= (path-info request)
                    login-uri))
        (println "Default workflow.")
        (cond
          ; The user is coming from their email trying to verify the login, so try to login.
          (:uuid params)
          (try
            (let [user-record (credential-fn
                                (with-meta params
                                           {::friend/workflow :form}))]
              ; Successful login!
              (println "Login successful: " (:username user-record))
              (workflows/make-auth user-record {::friend/workflow          :form
                                                ::friend/redirect-on-auth? true}))
            (catch ExceptionInfo e
              (let [{:keys [new-user]} (ex-data e)]
                (println "Login failed, new user: " new-user)
                (if new-user
                  (redirect-create-account {:uuid (:user/uuid new-user)})
                  (redirect-login-failed :uuid (:uuid params))))))

          true
          (redirect-login-failed))))))

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
        (println "Facebook workflow.")
        (cond
          ; Facebook login succeeded, Facebook will redirect to /login/fb?code=somecode.
          ; Use the returned code and get/validate access token before authenticating.
          (:code params)
          (let [validated-token (fb/validated-token app-id app-secret (:code params) (request-url request))]
            (println "Recieved validated token...")
            (if (:error validated-token)
              ; Redirect back to the login page on invalid token, something went wrong.
              (redirect-login-failed :error (:error validated-token))

              ; Try to get credentials for the facebook user. If there's no user account an exception
              ; is thrown and we need to prompt the user to create a new account
              (try
                (let [user-record (credential-fn
                                    (with-meta validated-token
                                               {::friend/workflow :facebook}))]
                  ; Successful login!
                  (println "Login successful: " (:username user-record))
                  (workflows/make-auth user-record {::friend/workflow          :facebook
                                                    ::friend/redirect-on-auth? true}))
                (catch ExceptionInfo e
                  ; The credential-fn did not find a user account for this Facebook user,
                  ; so catch the exception and redirect to create a new account
                  (let [{:keys [new-user]} (ex-data e)]
                    (println "No existing user account for: " new-user "... Redirecting...")
                    (redirect-create-account new-user))))))

          ; User cancelled or denied login, redirect back to the login page.
          (:error params)
          (redirect-login-failed :error (:error params))

          ; Redirect to Facebook login dialog
          true
          (do
            (println "Redirecting to Facebook...")
            (fb/login-dialog app-id (request-url request))))))))

(defn create-account
  [send-email-fn]
  (fn [{:keys [params ::friend/auth-config] :as request}]
    (let [credential-fn (get auth-config :credential-fn)
          login-uri (get auth-config :create-account-login-uri)]
      (when (= (path-info request)
               login-uri)
        (try
          (let [user-record (credential-fn (with-meta params
                                                      {::friend/workflow :create-account}))]
            (workflows/make-auth user-record {::friend/workflow :create-account
                                              ::friend/redirect-on-auth? true}))
          (catch ExceptionInfo e
            (let [{:keys [verification]} (ex-data e)]
              (if verification
                (do
                  (go
                    (send-email-fn verification))
                  (redirect-verify-email))
                (redirect-login-failed)))))))))