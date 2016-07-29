(ns eponai.server.auth.workflows
  (:require [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [cemerick.url :as url]
            [clojure.core.async :refer [go]]
            [eponai.server.external.facebook :as fb]
            [eponai.server.external.stripe :as stripe]
            [ring.util.response :as r]
            [ring.util.request :refer [path-info request-url]]
            [taoensso.timbre :refer [debug error info]])
  (:import (clojure.lang ExceptionInfo)))

(defn redirect-login-failed [message]
  (r/status (r/response {:status  :login-failed
                         :message (or message "Oops, something when wrong when creating your account. Try again later.")}) 500))

(defn redirect-activate-account [account-info]
  (r/redirect (str "/signup?new=Y&"
                   (url/map->query account-info))))

(defn redirect-verify-email []
  (r/status (r/response {:status :verification-needed
                         :message "Check your inbox for a verification email!"}) 500))

(defn form
  []
  (fn [{:keys [params ::friend/auth-config] :as request}]
    (let [credential-fn (get auth-config :credential-fn)
            login-uri (get auth-config :email-login-uri)]

      ; Verify that we're doing a request to /api/login/email otherwise skip this flow
      (when (and (= (path-info request)
                    login-uri))
        (cond
          ; The user is coming from their email trying to verify the login, so try to login.
          (:uuid params)
          (try
            (let [user-record (credential-fn
                                (with-meta params
                                           {::friend/workflow :form}))]
              ; Successful login!
              (debug "Login successful: " (:username user-record))
              (workflows/make-auth user-record {::friend/workflow          :form
                                                ::friend/redirect-on-auth? true}))
            (catch ExceptionInfo e
              (let [{:keys [activate-user]} (ex-data e)]
                (debug "Login failed. " (if activate-user "User not activated." "Invalid UUID."))
                (if activate-user
                  (redirect-activate-account {:uuid (:user/uuid activate-user)})
                  (redirect-login-failed (:uuid params))))))

          true
          (redirect-login-failed "Not found"))))))

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
        (debug "Facebook workflow.")
        (cond
          ; Facebook login succeeded, Facebook will redirect to /login/fb?code=somecode.
          ; Use the returned code and get/validate access token before authenticating.
          (:code params)
          (let [validated-token (fb/validated-token app-id app-secret (:code params) (request-url request))]
            (debug "Recieved validated token:" validated-token)
            (if (:error validated-token)
              ; Redirect back to the login page on invalid token, something went wrong.
              (redirect-login-failed "Facebook error")

              ; Try to get credentials for the facebook user. If there's no user account an exception
              ; is thrown and we need to prompt the user to create a new account
              (try
                (let [user-record (credential-fn
                                    (with-meta validated-token
                                               {::friend/workflow :facebook}))]
                  ; Successful login!
                  (debug "Login successful for user:" (:username user-record))
                  (workflows/make-auth user-record {::friend/workflow          :facebook
                                                    ::friend/redirect-on-auth? true}))
                (catch ExceptionInfo e
                  ; The user is not activated. Redirect to activate account
                  (let [{:keys [activate-user]} (ex-data e)]
                    (debug "No activated user account for user:" activate-user "redirecting user to activate account.")
                    (redirect-activate-account activate-user))))))

          ; User cancelled or denied login, redirect back to the login page.
          (:error params)
          (redirect-login-failed "Permission denied")

          ; Redirect to Facebook login dialog
          true
          (do
            (debug "Redirecting to Facebook")
            (fb/login-dialog app-id (request-url request))))))))

(defn facebook-mobile
  "Workflow for login with Facebook via the om.next structure.
  A parser is assoc'ed to the request if the mutation matches a login mutation. The parser is used here to fetch
  params from the mutation and perform the auth using those params.

  Returns an authentication map on successful auth, otherwise nil."
  [app-id app-secret]
  (fn [{:keys [login-parser ::friend/auth-config body facebook-token-validator] :as request}]
    (let [{:keys [login-mutation-uri credential-fn]} auth-config]
      (debug "Got request: " request)

      ;; Check conditions for performing this workflow and skip if not met:
      (when (and
              ; Cond 1) URL path matches the url for authenticating the user, /api
              (= (path-info request)
                 login-mutation-uri)
              ; Cond 2) Login parser has bees assoc'ed in the request to fetch login params from the mutation with.
              (some? login-parser)
              ; Cond 3) A facebook validator function is assoc'ed to the request to validate the fb token.
              (some? facebook-token-validator))
        (let [parsed-res (login-parser {} (:query body))
              fb-params (:result (get parsed-res 'signin/facebook))] ; Get params from the 'signin/facebook mutation result

          (debug "Parsed mutation: " parsed-res)
          ; Cond 4) For facebook login workflow we need the user-id and access-token for the user trying to auth.
          ; If nil we're probably doing some other auth type, so skip this workflow.
          (when (and (some? fb-params))
            (let [validated-token (facebook-token-validator app-id app-secret fb-params)]
              (if (:error validated-token)
                (do
                  (debug "Facebook login Error")
                  nil)
                (try
                  (let [user-record (credential-fn
                                      (with-meta validated-token
                                                 {::friend/workflow :facebook}))]
                    (debug "Facebook Successful login")
                    ;; Successful login, return authentication map.
                    (workflows/make-auth user-record {::friend/workflow :facebook
                                                      ::friend/redirect-on-auth? false}))
                  (catch ExceptionInfo e
                    (throw e)))))))))))

(defn create-account
  [send-email-fn]
  (fn [{:keys [body ::friend/auth-config ::stripe/stripe-fn] :as request}]
    (let [credential-fn (get auth-config :credential-fn)
          login-uri (get auth-config :activate-account-uri)]
      (when (= (path-info request)
               login-uri)
        (try
          (let [user-record (credential-fn (with-meta {:body body
                                                       :stripe-fn stripe-fn}
                                                      {::friend/workflow :activate-account}))]
            (prn "Successful login")
            (workflows/make-auth user-record {::friend/workflow :activate-account
                                              ::friend/redirect-on-auth? true}))
          (catch ExceptionInfo e
            (prn e)
            (let [{:keys [verification]} (ex-data e)]
              (if verification
                (do
                  (go
                    (send-email-fn verification))
                  (redirect-verify-email))
                (redirect-login-failed (.getMessage e))))))))))